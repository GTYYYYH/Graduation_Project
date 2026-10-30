package cn.wzbc.maintenance.attachment.service;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.nio.charset.StandardCharsets;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
public class AttachmentStorage {
    private final Path root;
    private final Set<String> active=ConcurrentHashMap.newKeySet();
    public AttachmentStorage(Environment env) throws IOException { root=Path.of(env.getRequiredProperty("campus.attachment-root")).toRealPath(); }
    public String write(byte[] bytes,String extension) throws IOException {
        String key=UUID.randomUUID()+"."+extension; Path path=path(key);
        active.add(key);
        try {
            Files.writeString(journal(key),"CAMPUS_UPLOAD_V1\n"+key+"\n",StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            Files.write(path,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE); return key;
        } catch(IOException failure) { try { abandon(key); } catch(IOException compensation) { failure.addSuppressed(compensation); } throw failure; }
    }
    /** A durable, server-created journal identifies this upload without guessing unknown files. */
    public void registered(String key) {
        try { Files.deleteIfExists(journal(key)); } catch(IOException failure) { /* Recovery verifies DB ownership before touching the file. */ }
        finally { active.remove(key); }
    }
    public void abandon(String key) throws IOException {
        try { delete(key); Files.deleteIfExists(journal(key)); } finally { active.remove(key); }
    }
    public record Recovery(int deleted,int retryPending) {}
    public Recovery recoverInterrupted(Predicate<String> registered) {
        int deleted=0,retry=0;
        try(var journals=Files.newDirectoryStream(root,"*.upload")) {
            for(Path entry:journals) {
                try {
                    if(!Files.isRegularFile(entry,LinkOption.NOFOLLOW_LINKS) || !entry.toRealPath().getParent().equals(root) || Files.size(entry)>200) continue;
                    String filename=entry.getFileName().toString(), key=filename.substring(0,filename.length()-7);
                    if(!key.matches("[0-9a-f-]{36}\\.(png|jpg)") || !Files.readString(entry,StandardCharsets.US_ASCII).equals("CAMPUS_UPLOAD_V1\n"+key+"\n") || active.contains(key)) continue;
                    // DB failures propagate; they are not proof that a file is unregistered.
                    if(registered.test(key)) { Files.delete(entry); continue; }
                    delete(key); Files.delete(entry); deleted++;
                } catch(IOException failure) { retry++; }
            }
        } catch(IOException failure) { retry++; }
        return new Recovery(deleted,retry);
    }
    private Path journal(String key) throws IOException { path(key); return root.resolve(key+".upload"); }
    public byte[] read(String key) throws IOException { return Files.readAllBytes(existing(key)); }
    public void delete(String key) throws IOException {
        Path path=path(key); if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)) { existing(key); Files.delete(path); }
    }
    private Path path(String key) throws IOException {
        if(!key.matches("[0-9a-f-]{36}\\.(png|jpg)")) throw new IOException("非法存储引用");
        Path p=root.resolve(key).normalize(); if(!p.getParent().equals(root)) throw new IOException("非法存储引用"); return p;
    }
    private Path existing(String key) throws IOException {
        Path p=path(key); if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || !p.toRealPath().getParent().equals(root)) throw new IOException("附件文件不可用"); return p;
    }
}
