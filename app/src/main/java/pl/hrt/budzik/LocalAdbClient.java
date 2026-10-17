package pl.hrt.budzik;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

/**
 * Eksperymentalny klient ADB TCP dla TCL Google TV z wlaczonym debugowaniem.
 * Rozmawia TYLKO z 127.0.0.1:5555. Nie uruchamia serwera ADB ani nie wymaga roota.
 * Posiada osobny klucz RSA; przy pierwszym uruchomieniu trzeba zatwierdzic
 * wyswietlone przez telewizor zapytanie o autoryzacje debugowania USB.
 *
 * Wariant protokolu ADB 1.0.0 z klasyczna autoryzacja RSA (bez TLS).
 */
public final class LocalAdbClient {
    private LocalAdbClient() {}
    private static final int ADB_PORT = 5555;
    private static final String HOST = "127.0.0.1";
    private static final int VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 4096;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_PUBKEY = 3;
    private static final byte[] SHA1_DER_PREFIX = hex("3021300906052b0e03021a05000414");
    private static final String PRIVATE_KEY_FILE = "local_adb_private.pk8";
    private static final String PUBLIC_KEY_FILE = "local_adb_public.der";

    public static final class Result {
        public final boolean ok;
        public final String detail;
        Result(boolean ok, String detail) { this.ok = ok; this.detail = detail; }
        @Override public String toString() { return detail; }
    }

    /** Musi byc wywolywane poza glownym watkiem UI. */
    public static Result execute(Context ctx, String command, int socketTimeoutMs) {
        if (!"input keyevent 224".equals(command)
                && !"getprop ro.build.version.sdk".equals(command)
                && !TclAudioOnly.ADB_SEQUENCE.equals(command)) {
            return new Result(false, "Niedozwolona komenda lokalnego ADB");
        }
        try {
            KeyPair key = getOrCreateKey(ctx);
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(HOST, ADB_PORT), 1500);
                s.setSoTimeout(socketTimeoutMs);
                DataInputStream in = new DataInputStream(s.getInputStream());
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                write(out, "CNXN", VERSION, MAX_PAYLOAD,
                        "host::\0".getBytes(StandardCharsets.UTF_8));
                boolean publicKeySent = false;
                boolean connected = false;
                for (int i = 0; i < 7; i++) {
                    Packet p = read(in);
                    if (p.is("CNXN")) { connected = true; break; }
                    if (p.is("AUTH") && p.arg0 == AUTH_TOKEN) {
                        if (p.data.length != 20) {
                            return new Result(false, "ADB AUTH: nieznana dlugosc tokena " + p.data.length);
                        }
                        if (!publicKeySent) {
                            // Najpierw podpis. Jesli klucz jest nieznany, adbd odesle AUTH TOKEN ponownie.
                            byte[] signed = signAdbToken(key.getPrivate(), p.data);
                            write(out, "AUTH", AUTH_SIGNATURE, 0, signed);
                            publicKeySent = true;
                        } else {
                            // Zglos klucz publiczny, aby TCL wyswietlil okno zezwolenia.
                            byte[] pub = (encodeAdbPubkey((RSAPublicKey) key.getPublic())
                                    + " hrt-budzik@tcl\0").getBytes(StandardCharsets.US_ASCII);
                            write(out, "AUTH", AUTH_PUBKEY, 0, pub);
                            // Przy pierwszym parowaniu uzytkownik musi nacisnac Zezwalaj.
                            // Moze nastapic timeout lub rozlaczenie: po zatwierdzeniu nacisnij test drugi raz.
                        }
                        continue;
                    }
                    return new Result(false, "ADB: nieoczekiwany pakiet " + p.name);
                }
                if (!connected) return new Result(false, "ADB: nie uzyskano autoryzacji (sprawdz komunikat na TV)");

                int localId = 1, remoteId = 0;
                write(out, "OPEN", localId, 0,
                        ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
                boolean opened = false, closed = false;
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                for (int i = 0; i < 70; i++) {
                    Packet p = read(in);
                    if (p.is("OKAY") && p.arg1 == localId) {
                        opened = true; remoteId = p.arg0;
                    } else if (p.is("WRTE") && p.arg1 == localId) {
                        opened = true; remoteId = p.arg0;
                        if (response.size() < 768) response.write(p.data, 0, Math.min(p.data.length, 768 - response.size()));
                        write(out, "OKAY", localId, remoteId, new byte[0]);
                    } else if (p.is("CLSE") && p.arg1 == localId) {
                        if (remoteId == 0) remoteId = p.arg0;
                        write(out, "CLSE", localId, remoteId, new byte[0]);
                        closed = true;
                        break;
                    }
                }
                String output = response.toString("UTF-8").replace('\n', ' ').trim();
                if (opened && closed) {
                    return new Result(true, "ADB localhost: shell wykonany" +
                            (output.isEmpty() ? "" : "; wynik: " + output));
                }
                return new Result(false, "ADB: brak potwierdzenia zakonczenia shell (opened=" + opened + ")");
            }
        } catch (java.net.ConnectException e) {
            return new Result(false, "ADB localhost:5555 odrzuca polaczenie. Sprawdz Debugowanie USB.");
        } catch (java.net.SocketTimeoutException e) {
            return new Result(false, "ADB: timeout. Przy pierwszej probie zaznacz 'Zawsze zezwalaj' i 'Zezwalaj' na TCL, potem ponow test.");
        } catch (Exception e) {
            return new Result(false, "ADB blad: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static KeyPair getOrCreateKey(Context ctx) throws Exception {
        File priv = new File(ctx.getFilesDir(), PRIVATE_KEY_FILE);
        File pub = new File(ctx.getFilesDir(), PUBLIC_KEY_FILE);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile()) {
            try (FileInputStream a = new FileInputStream(priv); FileInputStream b = new FileInputStream(pub)) {
                byte[] p = readAll(a, 4096);
                byte[] u = readAll(b, 4096);
                return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(u)),
                        factory.generatePrivate(new PKCS8EncodedKeySpec(p)));
            }
        }
        KeyPairGenerator kg = KeyPairGenerator.getInstance("RSA");
        kg.initialize(2048);
        KeyPair kp = kg.generateKeyPair();
        // Pliki sa prywatne dla aplikacji Android. Nie przesylaj ich na GitHub.
        try (FileOutputStream a = new FileOutputStream(priv); FileOutputStream b = new FileOutputStream(pub)) {
            a.write(kp.getPrivate().getEncoded());
            b.write(kp.getPublic().getEncoded());
        }
        return kp;
    }

    private static byte[] readAll(FileInputStream f, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024]; int n;
        while ((n = f.read(tmp)) != -1) {
            if (b.size() + n > max) throw new IOException("Nieprawidlowy plik klucza");
            b.write(tmp, 0, n);
        }
        return b.toByteArray();
    }

    // ADB podpisuje juz wstepnie zahaszowany (20 bajtow SHA1) token:
    // RSA_PKCS1_PADDING(DigestInfo(SHA1) || token), bez ponownego haszowania.
    private static byte[] signAdbToken(PrivateKey priv, byte[] token) throws Exception {
        byte[] payload = new byte[SHA1_DER_PREFIX.length + token.length];
        System.arraycopy(SHA1_DER_PREFIX, 0, payload, 0, SHA1_DER_PREFIX.length);
        System.arraycopy(token, 0, payload, SHA1_DER_PREFIX.length, token.length);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, priv);
        return cipher.doFinal(payload);
    }

    private static String encodeAdbPubkey(RSAPublicKey pk) throws Exception {
        BigInteger n = pk.getModulus();
        BigInteger exponent = pk.getPublicExponent();
        if (n.bitLength() != 2048 || !exponent.equals(BigInteger.valueOf(65537)))
            throw new IOException("ADB wymaga klucza RSA 2048 z wykladnikiem 65537");
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        BigInteger inv = two32.subtract(n.mod(two32).modInverse(two32)).mod(two32);
        BigInteger r = BigInteger.ONE.shiftLeft(2048);
        BigInteger rr = r.multiply(r).mod(n);
        ByteBuffer raw = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        raw.putInt(64);                    // modulus_size_words
        raw.putInt(inv.intValue());         // -n[0]^-1 (mod 2^32)
        raw.put(little(n, 256));
        raw.put(little(rr, 256));
        raw.putInt(exponent.intValue());
        return Base64.getEncoder().encodeToString(raw.array());
    }

    private static byte[] little(BigInteger n, int length) {
        byte[] be = n.toByteArray();
        byte[] le = new byte[length];
        for (int i = 0; i < length && i < be.length; i++) le[i] = be[be.length - i - 1];
        return le;
    }

    private static final class Packet {
        final String name; final int arg0; final int arg1; final byte[] data;
        Packet(String n, int a0, int a1, byte[] d) { name = n; arg0 = a0; arg1 = a1; data = d; }
        boolean is(String s) { return name.equals(s); }
    }

    private static Packet read(DataInputStream in) throws IOException {
        byte[] hdr = new byte[24]; in.readFully(hdr);
        ByteBuffer b = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = b.getInt(), a0 = b.getInt(), a1 = b.getInt();
        int len = b.getInt(), sum = b.getInt(), magic = b.getInt();
        if ((cmd ^ -1) != magic) throw new IOException("ADB magic nieprawidlowy");
        if (len < 0 || len > 1024 * 1024) throw new IOException("ADB: zly rozmiar pakietu " + len);
        byte[] data = new byte[len]; in.readFully(data);
        // W starszym wariancie protokolu ADB suma kontrolna dotyczy bajtow payloadu.
        int checksum = 0;
        for (byte d : data) checksum += d & 0xff;
        if (checksum != sum && sum != 0) throw new IOException("ADB: nieprawidlowa suma kontrolna");
        char[] name = new char[4];
        for (int i = 0; i < 4; i++) name[i] = (char) ((cmd >>> (8 * i)) & 0xff);
        return new Packet(new String(name), a0, a1, data);
    }

    private static void write(DataOutputStream out, String cmd, int a0, int a1, byte[] data) throws IOException {
        int command = (cmd.charAt(0) & 0xff) | ((cmd.charAt(1) & 0xff) << 8)
                | ((cmd.charAt(2) & 0xff) << 16) | ((cmd.charAt(3) & 0xff) << 24);
        int sum = 0; for (byte d : data) sum += d & 0xff;
        ByteBuffer head = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        head.putInt(command).putInt(a0).putInt(a1).putInt(data.length).putInt(sum).putInt(command ^ -1);
        out.write(head.array()); out.write(data); out.flush();
    }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i*2, i*2+2), 16);
        return b;
    }
}
