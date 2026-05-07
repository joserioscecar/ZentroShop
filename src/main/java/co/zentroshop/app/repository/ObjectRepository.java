package co.zentroshop.app.repository;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Repositorio genérico que permite persistir, recuperar, buscar, actualizar y
 * eliminar
 * colecciones de objetos en archivos binarios.
 *
 * @param <T> Tipo de objetos que serán almacenados en el repositorio
 *
 * @autor José David Ríos Pacheco
 * @versión 2
 */

public class ObjectRepository<T extends Serializable> implements Serializable {

    private final Path filePath;

    private List<T> collection;

    public ObjectRepository(String pathName) {
        if (pathName == null || pathName.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        this.filePath = Paths.get(pathName);
        this.collection = new ArrayList<>();
    }

    public boolean save(T object) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(object, "Cannot add a null object");

        if (collection.contains(object)) {
            throw new IllegalArgumentException("The object already exists in the collection.");
        }

        collection = getAll();
        boolean saved = collection.add(object);
        persist();

        return saved;
    }

    public List<T> getAll() throws IOException, ClassNotFoundException {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(filePath)))) {
            collection = (List<T>) ois.readObject();
            return new ArrayList<>(collection);
        }
    }

    private void persist() throws IOException {
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(filePath)))) {
            oos.writeObject(collection);
            oos.flush();
        }
    }

    public boolean remove(T object) throws IOException, ClassNotFoundException {
        if (object == null) {
            throw new IllegalArgumentException("Cannot remove a null object");
        }

        collection = getAll();
        boolean removed = collection.remove(object);
        if (removed) {
            persist();
        }
        return removed;
    }

    public Optional<T> find(Predicate<? super T> predicate) throws IOException, ClassNotFoundException {
        if (predicate == null) {
            throw new IllegalArgumentException("Predicate cannot be null");
        }

        collection = getAll();

        for (T element : collection) {
            if (predicate.test(element)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    public boolean update(T object) throws IOException {
        Objects.requireNonNull(object, "Object cannot be null");

        int index = collection.indexOf(object);
        if (index >= 0) {
            collection.set(index, object);
            persist();
            return true;
        }
        return false;
    }

    public FilterBuilder<T> filter(Predicate<? super T> predicate) throws IOException, ClassNotFoundException {
        if (predicate == null) {
            throw new IllegalArgumentException("Filter predicate cannot be null");
        }
        collection = getAll();
        return new FilterBuilder<>(collection, predicate);
    }

    public int indexOf(Predicate<? super T> predicate) throws IOException, ClassNotFoundException {
        if (predicate == null) {
            throw new IllegalArgumentException("Predicate cannot be null");
        }

        collection = getAll();

        for (int i = collection.size() - 1; i >= 0; i--) {
            if (predicate.test(collection.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public static class FilterBuilder<T> {

        private final List<T> collection;
        private Predicate<T> predicate;

        public FilterBuilder(List<T> collection, Predicate<? super T> predicate) {
            this.collection = collection;
            this.predicate = predicate::test;
        }

        public FilterBuilder<T> and(Predicate<? super T> otro) {
            if (otro == null) {
                throw new IllegalArgumentException("Filter predicate cannot be null");
            }
            this.predicate = this.predicate.and(otro::test);
            return this;
        }

        public List<T> get() {
            List<T> result = new ArrayList<>();
            for (T element : collection) {
                if (predicate.test(element)) {
                    result.add(element);
                }
            }
            return result;
        }

        public <R> List<R> map(Function<? super T, ? extends R> mapper) {
            List<R> result = new ArrayList<>();
            for (T element : collection) {
                if (predicate.test(element)) {
                    result.add(mapper.apply(element));
                }
            }
            return result;
        }
    }

}