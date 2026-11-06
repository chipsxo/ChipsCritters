package dev.chipsxo.chipscritters;
final class RecordNextState {
 enum State { NO_ROUTE, WAIT_SAFARI, WAIT_BUILD, NO_WAYPOINT, READY }
 static State check(boolean active,boolean safari,boolean building,boolean target){
  if(!active)return State.NO_ROUTE;if(!safari)return State.WAIT_SAFARI;
  if(building)return State.WAIT_BUILD;return target?State.READY:State.NO_WAYPOINT;
 }
}
