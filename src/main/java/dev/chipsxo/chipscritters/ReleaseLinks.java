package dev.chipsxo.chipscritters;
/** Public read-only endpoints; no credentials or write access. */
public final class ReleaseLinks {
 public static final String REPOSITORY="https://github.com/chipsxo/ChipsCritters";
 public static final String ISSUES=REPOSITORY+"/issues";
 public static final String RELEASES=REPOSITORY+"/releases";
 public static final String ROUTES="https://raw.githubusercontent.com/chipsxo/ChipsCritters/main/routes-signed.json";
 private ReleaseLinks(){}
}
