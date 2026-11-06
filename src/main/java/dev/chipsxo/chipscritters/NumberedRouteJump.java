package dev.chipsxo.chipscritters;

/** Converts the HUD's displayed number to the same pending-list cursor used by back/skip. */
final class NumberedRouteJump {
    static int index(int number,int currentNumber,int cursor,int count,int total) {
        if(number<1||number>total)return -1;
        int index=number-(currentNumber-cursor);
        return index>=0&&index<count?index:-1;
    }
}
