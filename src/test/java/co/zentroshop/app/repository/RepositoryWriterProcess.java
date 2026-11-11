package co.zentroshop.app.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Child JVM used by the repository's cross-process concurrency regression test. */
public final class RepositoryWriterProcess {
    public static void main(String[] args) throws Exception {
        var repository = new ObjectRepositoryTest.TestRepository(Path.of(args[0]));
        int firstId = Integer.parseInt(args[1]);
        int count = Integer.parseInt(args[2]);
        Files.createFile(Path.of(args[3]));
        Path gate = Path.of(args[4]);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!Files.exists(gate)) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Start signal not received");
            }
            Thread.sleep(10);
        }
        for (int i = 0; i < count; i++) {
            repository.save(new ObjectRepositoryTest.Row(firstId + i, "value"));
        }
    }
}
