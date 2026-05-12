package co.zentroshop.app.repository;

import co.zentroshop.app.repository.exception.DuplicateEntityException;
import co.zentroshop.app.repository.exception.EntityNotFoundException;
import co.zentroshop.app.repository.exception.StorageException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Repositorio genérico que permite persistir, recuperar, buscar, actualizar y
 * eliminar colecciones de objetos en archivos binarios.
 *
 * @param <T>  Tipo de objetos que serán almacenados en el repositorio
 * @param <ID> Tipo del identificador único de cada objeto
 *
 * @autor José David Ríos Pacheco
 * @versión 4
 */
public abstract class ObjectRepository<T extends Serializable, ID> implements Serializable {

    private final Path filePath;
    private final Function<T, ID> idExtractor;
    private List<T> collection;

    protected ObjectRepository(String pathName, Function<T, ID> idExtractor) {
        if (pathName == null || pathName.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        Objects.requireNonNull(idExtractor, "ID extractor cannot be null");
        this.filePath = Paths.get(pathName);
        this.collection = new ArrayList<>();
        this.idExtractor = idExtractor;
    }

    // ── Persistencia interna ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<T> getAll() {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(filePath)))) {
            collection = (List<T>) ois.readObject();
            return new ArrayList<>(collection);
        } catch (IOException e) {
            throw new StorageException("Failed to read from file: " + filePath, e);
        } catch (ClassNotFoundException e) {
            throw new StorageException("Incompatible data format in file: " + filePath, e);
        }
    }

    private void persist() {
        Path parentDir = filePath.getParent();
        try {
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(filePath)))) {
                oos.writeObject(collection);
                oos.flush();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to write to file: " + filePath, e);
        }
    }

    // ── Guardar ──────────────────────────────────────────────────────────────

    public boolean save(T object) {
        Objects.requireNonNull(object, "Cannot add a null object");

        collection = getAll();
        ID id = idExtractor.apply(object);

        boolean exists = collection.stream().anyMatch(e -> idExtractor.apply(e).equals(id));
        if (exists) {
            throw new DuplicateEntityException(id);
        }

        boolean saved = collection.add(object);
        persist();
        return saved;
    }

    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        Objects.requireNonNull(entities, "Entities cannot be null");

        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            save(entity);
            saved.add(entity);
        }
        return saved;
    }

    // ── Buscar ───────────────────────────────────────────────────────────────

    public Optional<T> findById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        collection = getAll();
        for (T element : collection) {
            if (idExtractor.apply(element).equals(id)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    public T getById(ID id) {
        return findById(id).orElseThrow(() -> new EntityNotFoundException(id));
    }

    public boolean existsById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");
        return findById(id).isPresent();
    }

    public List<T> findAll() {
        return getAll();
    }

    public List<T> findAll(Comparator<T> sort) {
        Objects.requireNonNull(sort, "Sort comparator cannot be null");

        List<T> sorted = getAll();
        sorted.sort(sort);
        return sorted;
    }

    public Page<T> findAll(int pageNumber, int pageSize) {
        if (pageNumber < 0) throw new IllegalArgumentException("Page number must be >= 0");
        if (pageSize < 1)   throw new IllegalArgumentException("Page size must be >= 1");

        List<T> all   = getAll();
        int fromIndex = pageNumber * pageSize;
        int toIndex   = Math.min(fromIndex + pageSize, all.size());

        List<T> content = fromIndex >= all.size() ? new ArrayList<>() : all.subList(fromIndex, toIndex);
        return new Page<>(new ArrayList<>(content), pageNumber, pageSize, all.size());
    }

    public Page<T> findAll(int pageNumber, int pageSize, Comparator<T> sort) {
        Objects.requireNonNull(sort, "Sort comparator cannot be null");
        if (pageNumber < 0) throw new IllegalArgumentException("Page number must be >= 0");
        if (pageSize < 1)   throw new IllegalArgumentException("Page size must be >= 1");

        List<T> all = getAll();
        all.sort(sort);

        int fromIndex   = pageNumber * pageSize;
        int toIndex     = Math.min(fromIndex + pageSize, all.size());

        List<T> content = fromIndex >= all.size() ? new ArrayList<>() : all.subList(fromIndex, toIndex);
        return new Page<>(new ArrayList<>(content), pageNumber, pageSize, all.size());
    }

    public long count() {
        return getAll().size();
    }

    public Optional<T> find(Predicate<? super T> predicate) {
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

    // ── Actualizar ───────────────────────────────────────────────────────────

    public boolean update(T object) {
        Objects.requireNonNull(object, "Object cannot be null");

        collection = getAll();
        ID id = idExtractor.apply(object);

        for (int i = 0; i < collection.size(); i++) {
            if (idExtractor.apply(collection.get(i)).equals(id)) {
                collection.set(i, object);
                persist();
                return true;
            }
        }
        throw new EntityNotFoundException(id);
    }

    // ── Eliminar ─────────────────────────────────────────────────────────────

    public boolean remove(T object) {
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

    public boolean deleteById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        collection = getAll();
        boolean removed = collection.removeIf(e -> idExtractor.apply(e).equals(id));
        if (!removed) {
            throw new EntityNotFoundException(id);
        }
        persist();
                
        return  removed;
    }

    public void delete(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        deleteById(idExtractor.apply(entity));
    }

    public void deleteAll(Iterable<? extends T> entities) {
        Objects.requireNonNull(entities, "Entities cannot be null");
        for (T entity : entities) {
            delete(entity);
        }
    }

    public void deleteAll() {
        collection = new ArrayList<>();
        persist();
    }

    // ── Filtros ──────────────────────────────────────────────────────────────

    public FilterBuilder<T> filter(Predicate<? super T> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Filter predicate cannot be null");
        }
        collection = getAll();
        return new FilterBuilder<>(collection, predicate);
    }

    // ── FilterBuilder ────────────────────────────────────────────────────────

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

    // ── Page ─────────────────────────────────────────────────────────────────

    public static class Page<T> implements Serializable {

        private final List<T> content;
        private final int pageNumber;
        private final int pageSize;
        private final long totalElements;

        public Page(List<T> content, int pageNumber, int pageSize, long totalElements) {
            this.content       = content;
            this.pageNumber    = pageNumber;
            this.pageSize      = pageSize;
            this.totalElements = totalElements;
        }

        public List<T> getContent()    { return content; }
        public int getPageNumber()     { return pageNumber; }
        public int getPageSize()       { return pageSize; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages()     { return (int) Math.ceil((double) totalElements / pageSize); }
        public boolean hasNext()       { return pageNumber + 1 < getTotalPages(); }
        public boolean hasPrevious()   { return pageNumber > 0; }
        public boolean isEmpty()       { return content.isEmpty(); }

        @Override
        public String toString() {
            return String.format("Page %d of %d (total: %d)", pageNumber + 1, getTotalPages(), totalElements);
        }
    }
}


