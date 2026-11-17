package com.householdfinance.service;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

/** Restricted OOXML intake: bound expansion and structural work before POI loads a workbook. */
final class WorkbookSafety {
    static final int MAX_FILE=8*1024*1024, MAX_PART=8*1024*1024, MAX_EXPANDED=32*1024*1024;
    static final int MAX_SHEETS=48, MAX_ROWS=10_000, MAX_COLUMNS=256, MAX_CELLS=100_000;
    private WorkbookSafety() {}

    static Workbook open(MultipartFile file) throws IOException {
        if (file==null || file.isEmpty() || file.getSize()>MAX_FILE)
            throw new IllegalArgumentException("Provide an XLSX workbook up to 8 MiB.");
        byte[] bytes;
        try(var input=file.getInputStream()) { bytes=input.readNBytes(MAX_FILE+1); }
        if (bytes.length>MAX_FILE || bytes.length<4 || bytes[0]!='P' || bytes[1]!='K')
            throw new IllegalArgumentException("Only bounded OOXML XLSX workbooks are supported.");
        preflight(bytes);
        Workbook workbook=WorkbookFactory.create(new ByteArrayInputStream(bytes));
        if (workbook.getNumberOfSheets()>MAX_SHEETS) {
            workbook.close();
            throw new IllegalArgumentException("Workbook has too many sheets.");
        }
        return workbook;
    }

    private static void preflight(byte[] bytes) {
        Set<String> names=new HashSet<>();
        long deadline=System.nanoTime()+5_000_000_000L;
        var budget=new StructureBudget(deadline);
        int expanded=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                budget.time();
                String name=entry.getName();
                if (name.length()>512 || name.startsWith("/") || name.contains("\\") || name.contains("%")
                        || name.contains(":") || Arrays.stream(name.split("/")).anyMatch(p->p.equals(".")||p.equals("..")))
                    throw new IllegalArgumentException("Unsupported workbook part path.");
                if (!names.add(name.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Duplicate workbook part names are not permitted.");
                if (names.size()>2048) throw new IllegalArgumentException("Workbook has too many parts.");
                var part=new ByteArrayOutputStream();
                byte[] buffer=new byte[8192];int count;
                while((count=zip.read(buffer))!=-1) {
                    budget.time();
                    expanded+=count;
                    if (expanded>MAX_EXPANDED || part.size()+count>MAX_PART)
                        throw new IllegalArgumentException("Workbook expanded content exceeds the intake limit.");
                    part.write(buffer,0,count);
                }
                String canonicalName=name.toLowerCase(Locale.ROOT);
                if (canonicalName.endsWith(".xml") || canonicalName.endsWith(".rels")) {
                    var factory=SAXParserFactory.newInstance();
                    factory.setNamespaceAware(true);
                    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
                    factory.setXIncludeAware(false);
                    var parser=factory.newSAXParser();
                    parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");
                    parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
                    parser.parse(new ByteArrayInputStream(part.toByteArray()),budget);
                }
            }
            if (!names.contains("[content_types].xml") || !names.contains("xl/workbook.xml"))
                throw new IllegalArgumentException("Complete XLSX workbook parts are required.");
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Workbook structure is invalid or exceeds safe intake bounds."); }
    }

    private static final class StructureBudget extends DefaultHandler {
        final long deadline;
        int nodes,cells,sheets,rows,depth,textDepth=-1,textLength;
        StructureBudget(long deadline) { this.deadline=deadline; }
        void time() { if (System.nanoTime()>deadline) throw new IllegalArgumentException("Workbook inspection exceeded its time limit."); }
        @Override public void startDocument() { rows=0;depth=0;textDepth=-1;textLength=0; }
        @Override public void startElement(String uri,String local,String qName,Attributes attrs) throws SAXException {
            time();depth++;
            if (++nodes>200_000 || depth>64) throw new SAXException("Structure limit");
            if (local.equals("sheet") && ++sheets>MAX_SHEETS) throw new SAXException("Sheet limit");
            if (local.equals("row")) {
                if (++rows>MAX_ROWS) throw new SAXException("Row limit");
                if (attrs.getValue("r")!=null) {
                    int row=Integer.parseInt(attrs.getValue("r"));
                    if (row<1 || row>MAX_ROWS) throw new SAXException("Row limit");
                }
            }
            if (local.equals("c")) {
                if (++cells>MAX_CELLS) throw new SAXException("Cell limit");
                String ref=attrs.getValue("r");int column=0,offset=0;
                if (ref!=null) {
                    while(offset<ref.length()) {
                        char c=Character.toUpperCase(ref.charAt(offset));
                        if (c<'A'||c>'Z') break;
                        column=column*26+c-'A'+1;offset++;
                        if (column>MAX_COLUMNS) throw new SAXException("Column limit");
                    }
                    if (offset==0 || offset==ref.length()) throw new SAXException("Cell reference");
                    int row=Integer.parseInt(ref.substring(offset));
                    if (row<1 || row>MAX_ROWS) throw new SAXException("Row limit");
                }
            }
            if (local.equals("col")) for(String key:List.of("min","max")) {
                if (attrs.getValue(key)!=null) {
                    int column=Integer.parseInt(attrs.getValue(key));
                    if (column<1 || column>MAX_COLUMNS) throw new SAXException("Column limit");
                }
            }
            if (textDepth<0 && (local.equals("c")||local.equals("si"))) { textDepth=depth;textLength=0; }
        }
        @Override public void characters(char[] chars,int start,int length) throws SAXException {
            time();
            if (textDepth>=0 && (textLength+=length)>2000) throw new SAXException("Cell text limit");
        }
        @Override public void endElement(String uri,String local,String qName) { if(depth==textDepth)textDepth=-1;depth--; }
    }
}
