import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
/** Offline only. Private keys are never read by the Minecraft mod. */
public class RouteSigner {
 public static void main(String[] args) throws Exception {
  if(args.length==2 && args[0].equals("keygen")) {
   Path folder=Path.of(args[1]); Files.createDirectories(folder);
   if(Files.exists(folder.resolve("private.pk8")))throw new IllegalStateException("Refusing to replace an existing private key");
   var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
   Files.write(folder.resolve("private.pk8"),keys.getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW);
   Files.writeString(folder.resolve("public-key.txt"),Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),StandardOpenOption.CREATE_NEW);
   System.out.println("Public key: "+Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())); return;
  }
  if(args.length==4 && args[0].equals("sign")) {
   byte[] data=Files.readAllBytes(Path.of(args[2]));
   var key=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(args[1]))));
   var signer=Signature.getInstance("Ed25519");signer.initSign(key);signer.update(data);
   String out="{\"payload\":\""+Base64.getEncoder().encodeToString(data)+"\",\"signature\":\""+Base64.getEncoder().encodeToString(signer.sign())+"\"}\n";
   Files.writeString(Path.of(args[3]),out);System.out.println("Wrote signed data: "+args[3]);return;
  }
  throw new IllegalArgumentException("keygen <private-folder> OR sign <private.pk8> <routes-export.json> <routes-signed.json>");
 }
}
