package com.householdfinance.member;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberRepository extends JpaRepository<MemberRecord,String> {
  List<MemberRecord> findByKindOrderByUpdatedAtDesc(String kind);
}
