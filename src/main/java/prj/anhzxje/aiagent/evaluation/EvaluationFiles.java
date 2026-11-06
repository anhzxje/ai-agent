package prj.anhzxje.aiagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

final class EvaluationFiles {
    static final ObjectMapper JSON=new ObjectMapper();
    private EvaluationFiles() {}
    static Path inside(Path root,String relative) throws IOException {
        if(relative==null || Path.of(relative).isAbsolute()) throw new IllegalArgumentException("Expected relative evaluation path");
        Path realRoot=root.toRealPath(),path=realRoot.resolve(relative).normalize().toRealPath();
        if(!path.startsWith(realRoot)) throw new IllegalArgumentException("Path leaves evaluation root");
        return path;
    }
    static String sha(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    static String hash(Object value) throws IOException {return sha(JSON.writeValueAsBytes(value));}
    static byte[] read(Path file) throws IOException {
        if(Files.size(file)>2*1024*1024) throw new IllegalArgumentException("Evaluation file too large");
        return Files.readAllBytes(file);
    }
    static String treeHash(Path root) throws IOException {
        var manifest=new StringBuilder();
        try(var paths=Files.walk(root)) {
            for(Path p:paths.sorted().toList()) {
                if(Files.isSymbolicLink(p)) throw new IllegalArgumentException("Fixture symlinks are not permitted");
                if(Files.isRegularFile(p)) {
                    String name=p.getFileName().toString();
                    if(!name.matches("[A-Za-z0-9_-]+\\.(java|xml|txt)") || name.startsWith(".env")) throw new IllegalArgumentException("Unapproved fixture file type/name");
                    manifest.append(root.relativize(p).toString().replace('\\','/')).append(':').append(sha(read(p))).append('\n');
                }
            }
        }
        return sha(manifest.toString().getBytes(StandardCharsets.UTF_8));
    }
    static void create(Path file,Object value) throws IOException {
        Files.write(file,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value),StandardOpenOption.CREATE_NEW);
    }
    static String redact(String text) {
        if(text==null) return null;
        return text.replaceAll("AIza[A-Za-z0-9_-]{20,}","[REDACTED]")
            .replaceAll("sk-[A-Za-z0-9_-]{16,}","[REDACTED]")
            .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+","Bearer [REDACTED]")
            .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+","[REDACTED]")
            .replaceAll("(?i)((?:api[_-]?key|password|secret|access[_-]?token)\\s*[:=]\\s*)[^\\s,;]+","$1[REDACTED]");
    }
}
