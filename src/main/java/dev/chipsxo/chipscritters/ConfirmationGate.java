package dev.chipsxo.chipscritters;
/** A screen may apply its action once, only after explicit confirmation. */
final class ConfirmationGate {
 private boolean closed;
 boolean confirm(Runnable action){if(closed)return false;closed=true;action.run();return true;}
 void cancel(){closed=true;}
}
