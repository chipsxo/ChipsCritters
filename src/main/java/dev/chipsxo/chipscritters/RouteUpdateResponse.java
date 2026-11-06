package dev.chipsxo.chipscritters;
/** Reject failed transports before staging; callers keep the last active snapshot on any exception. */
final class RouteUpdateResponse {
 static SignedRouteData.Verified verify(int status,String body,String publicKey,String modVersion,int currentVersion)throws Exception{
  if(status!=200||body==null)throw new java.io.IOException(status==0?"offline/no response":"HTTP "+status);
  return SignedRouteData.verify(body,publicKey,modVersion,currentVersion);
 }
}
