// One same-origin transport for every workspace, including legacy views.
export function installWorkspaceTransport() {
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (input, init) => {
    const url = new URL(input instanceof Request ? input.url : String(input), window.location.href);
    if (url.origin !== window.location.origin) return nativeFetch(input, init);
    const headers = new Headers(input instanceof Request ? input.headers : undefined);
    new Headers(init?.headers).forEach((value,key) => headers.set(key,value));
    headers.set('X-Workspace-Request','1');
    const response = await nativeFetch(input, {...init, headers, credentials:'same-origin'});
    if (response.status === 401 && !url.pathname.endsWith('/unlock')) window.dispatchEvent(new Event('workspace-locked'));
    return response;
  };
}
export async function workspacePost(path: string, body: unknown) {
  const response=await fetch('/api/workspace/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  if (!response.ok) {
    let message=await response.text();
    try { message=JSON.parse(message).error || message; } catch { /* plain access-filter error */ }
    if (response.status===401 && path==='unlock') message='Incorrect workspace password';
    if (response.status===429) message='Too many unlock attempts. Wait a minute and try again.';
    throw new Error(message || 'Workspace request failed');
  }
  return response;
}
