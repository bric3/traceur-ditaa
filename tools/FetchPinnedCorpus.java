//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//SOURCES FetchCorpus.java
// SPDX-License-Identifier: MPL-2.0

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/** Recreates the corpus from pinned provenance without relying on a new code search. */
class FetchPinnedCorpus {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Usage: jbang tools/FetchPinnedCorpus.java MANIFEST EXTERNAL_CACHE");
        Path inputs=Files.createDirectories(Path.of(args[1]).resolve("inputs"));
        var http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        Set<String> seen=new HashSet<>();
        int failures=0;
        StringBuilder errors=new StringBuilder("id\tdetail\n");
        for(String row:Files.readAllLines(Path.of(args[0]))) {
            if(row.startsWith("id\t"))continue;
            String[] fields=row.split("\t",-1);
            if(fields.length!=7 || !fields[0].matches("[0-9a-f]{64}") || !fields[0].equals(fields[5])
                    || !fields[1].matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || !fields[2].matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("Invalid provenance row");
            }
            String hash=fields[0],repo=fields[1],revision=fields[2],path=fields[3];
            if(!seen.add(hash))continue;
            String lower=path.toLowerCase(Locale.ROOT);
            if(path.startsWith("/") || path.contains("..") || !(lower.endsWith(".ditaa") || lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".adoc") || lower.endsWith(".puml") || lower.endsWith(".plantuml"))) {
                throw new IllegalArgumentException("Manifest path is not a diagram or documentation file: "+path);
            }
            try {
                Path target=inputs.resolve(hash+".ditaa");
                if(Files.isRegularFile(target) && sha256(Files.readAllBytes(target)).equals(hash))continue;
                URI url=new URI("https","raw.githubusercontent.com","/"+repo+"/"+revision+"/"+path,null);
                var request=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30)).header("User-Agent","traceur-ditaa-corpus").build();
                var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());
                if(response.statusCode()!=200)throw new IllegalStateException("HTTP "+response.statusCode());
                byte[] bytes=response.body();
                boolean raw=lower.endsWith(".ditaa") || lower.endsWith(".txt") && (lower.contains("ditaa") || path.startsWith("test-resources/text/"));
                if(!raw) {
                    var blocks=FetchCorpus.extract(new String(bytes,StandardCharsets.UTF_8),repo.equals("stathissideris/ditaa") && path.equals("README.md"));
                    bytes=blocks.get(Integer.parseInt(fields[4])-1).getBytes(StandardCharsets.UTF_8);
                }
                if(!sha256(bytes).equals(hash))throw new IllegalStateException("Input checksum differs from pinned manifest");
                Files.write(target,bytes);
            } catch(Exception e) {
                failures++;
                errors.append(hash).append('\t').append(e.getMessage().replace('\n',' ')).append('\n');
            }
        }
        Files.writeString(Path.of(args[1]).resolve("fetch-errors.tsv"),errors);
        System.out.println("Verified "+(seen.size()-failures)+" unique inputs; "+failures+" failures");
        if(failures>0)throw new IllegalStateException("Corpus fetch failed; inspect fetch-errors.tsv");
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
