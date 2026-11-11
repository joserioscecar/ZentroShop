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
 * eliminar colecciones de objetos en archivos binarios.
 *
 * @param <T>  Tipo de objetos que serán almacenados en el repositorio
 * @param <ID> Tipo del identificador único de cada objeto
 *
 * @autor José David Ríos Pacheco
 * @versión 4
 */
public abstract class ObjectRepository<T extends Serializable, ID> {

    public static class DuplicateEntityException extends RuntimeException {
        public DuplicateEntityException(Object id) { super("Entity with ID '" + id + "' already exists"); }
        public DuplicateEntityException(Object id, String message) { super("Entity with ID '" + id + "' already exists: " + message); }
        public DuplicateEntityException(Object id, Throwable cause) { super("Entity with ID '" + id + "' already exists", cause); }
        public DuplicateEntityException(String message) { super(message); }
        public DuplicateEntityException(String message, Throwable cause) { super(message, cause); }
    }

    public static class EntityNotFoundException extends RuntimeException {
        public EntityNotFoundException(Object id) { super("Entity with ID '" + id + "' not found"); }
        public EntityNotFoundException(Object id, String message) { super("Entity with ID '" + id + "' not found: " + message); }
        public EntityNotFoundException(Object id, Throwable cause) { super("Entity with ID '" + id + "' not found", cause); }
        public EntityNotFoundException(String message) { super(message); }
        public EntityNotFoundException(String message, Throwable cause) { super(message, cause); }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message) { super(message); }
        public StorageException(String message, Throwable cause) { super(message, cause); }
        public StorageException(Throwable cause) { super(cause); }
    }


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

    private ID extractId(T object) {
        ID id = idExtractor.apply(Objects.requireNonNull(object, "Object cannot be null"));
        return Objects.requireNonNull(id, "ID cannot be null");
    }

    // ── Persistencia interna ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<T> getAll() {
        try {
            if (!Files.exists(filePath) || Files.size(filePath) == 0) {
                collection = new ArrayList<>();
                return new ArrayList<>();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to read from file: " + filePath, e);
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(filePath)))) {
            collection = (List<T>) ois.readObject();
            return new ArrayList<>(collection);
        } catch (EOFException e) {
            collection = new ArrayList<>();
            return new ArrayList<>();
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

    public void save(T object) {
        Objects.requireNonNull(object, "Cannot add a null object");

        collection = getAll();
        ID id = extractId(object);

        boolean exists = collection.stream().anyMatch(e -> Objects.equals(extractId(e), id));
        if (exists) {
            throw new DuplicateEntityException(id);
        }

        collection.add(object);
        persist();
    }

    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        Objects.requireNonNull(entities, "Entities cannot be null");

        List<S> batch = new ArrayList<>();
        for (S entity : entities) {
            Objects.requireNonNull(entity, "Entities cannot contain null");
            batch.add(entity);
        }
        if (batch.isEmpty()) {
            return new ArrayList<>();
        }

        Set<ID> batchIds = new HashSet<>();
        for (S entity : batch) {
            ID id = extractId(entity);
            if (!batchIds.add(id)) {
                throw new DuplicateEntityException(id);
            }
        }

        collection = getAll();
        Set<ID> existingIds = new HashSet<>();
        for (T element : collection) {
            existingIds.add(extractId(element));
        }
        for (ID id : batchIds) {
            if (existingIds.contains(id)) {
                throw new DuplicateEntityException(id);
            }
        }

        collection.addAll(batch);
        persist();

        return new ArrayList<>(batch);
    }

    // ── Buscar ───────────────────────────────────────────────────────────────

    public Optional<T> findById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        collection = getAll();
        for (T element : collection) {
            if (Objects.equals(extractId(element), id)) {
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

    public void update(T object) {
        Objects.requireNonNull(object, "Object cannot be null");

        collection = getAll();
        ID id = extractId(object);

        for (int i = 0; i < collection.size(); i++) {
            if (Objects.equals(extractId(collection.get(i)), id)) {
                collection.set(i, object);
                persist();
                return;
            }
        }
        throw new EntityNotFoundException(id);
    }

    public boolean deleteById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        collection = getAll();
        boolean removed = collection.removeIf(e -> Objects.equals(extractId(e), id));

        if (!removed) {
            return false;
        }

        persist();

        return true;
    }

    public void delete(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        ID id = extractId(entity);
        if (!deleteById(id)) {
            throw new EntityNotFoundException(id);
        }
    }

    public void deleteAll(Iterable<? extends T> entities) {
        Objects.requireNonNull(entities, "Entities cannot be null");

        List<T> batch = new ArrayList<>();
        for (T entity : entities) {
            Objects.requireNonNull(entity, "Entities cannot contain null");
            batch.add(entity);
        }
        if (batch.isEmpty()) {
            return;
        }

        Set<ID> idsToDelete = new HashSet<>();
        for (T entity : batch) {
            idsToDelete.add(extractId(entity));
        }

        collection = getAll();
        Set<ID> existingIds = new HashSet<>();
        for (T element : collection) {
            existingIds.add(extractId(element));
        }
        for (ID id : idsToDelete) {
            if (!existingIds.contains(id)) {
                throw new EntityNotFoundException(id);
            }
        }

        collection.removeIf(e -> idsToDelete.contains(extractId(e)));
        persist();
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
        return new FilterBuilder<>(new ArrayList<>(collection), predicate);
    }

    // ── FilterBuilder ────────────────────────────────────────────────────────

    public static class FilterBuilder<T> {

        private final List<T> collection;
        private Predicate<T> predicate;

        public FilterBuilder(List<T> collection, Predicate<? super T> predicate) {
            this.collection = new ArrayList<>(Objects.requireNonNull(collection, "Collection cannot be null"));
            this.predicate = Objects.requireNonNull(predicate, "Filter predicate cannot be null")::test;
        }

        public FilterBuilder<T> and(Predicate<? super T> otro) {
            if (otro == null) {
                throw new IllegalArgumentException("Filter predicate cannot be null");
            }
            this.predicate = this.predicate.and(otro::test);
            return this;
        }

        public FilterBuilder<T> or(Predicate<? super T> otro) {
            if (otro == null) {
                throw new IllegalArgumentException("Filter predicate cannot be null");
            }
            this.predicate = this.predicate.or(otro::test);
            return this;
        }

        public FilterBuilder<T> not() {
            this.predicate = this.predicate.negate();
            return this;
        }

        public Optional<T> first() {
            for (T element : collection) {
                if (predicate.test(element)) {
                    return Optional.of(element);
                }
            }
            return Optional.empty();
        }

        public long count() {
            long matches = 0;
            for (T element : collection) {
                if (predicate.test(element)) {
                    matches++;
                }
            }
            return matches;
        }

        public boolean exists() {
            for (T element : collection) {
                if (predicate.test(element)) {
                    return true;
                }
            }
            return false;
        }

        public List<T> limit(int maxSize) {
            if (maxSize < 0) throw new IllegalArgumentException("Max size must be >= 0");
            List<T> result = new ArrayList<>();
            for (T element : collection) {
                if (predicate.test(element)) {
                    if (result.size() >= maxSize) {
                        break;
                    }
                    result.add(element);
                }
            }
            return result;
        }

        public Page<T> page(int pageNumber, int pageSize) {
            if (pageNumber < 0) throw new IllegalArgumentException("Page number must be >= 0");
            if (pageSize < 1)   throw new IllegalArgumentException("Page size must be >= 1");
            List<T> matches = get();
            int fromIndex = pageNumber * pageSize;
            int toIndex   = Math.min(fromIndex + pageSize, matches.size());
            List<T> content = fromIndex >= matches.size() ? new ArrayList<>() : matches.subList(fromIndex, toIndex);
            return new Page<>(new ArrayList<>(content), pageNumber, pageSize, matches.size());
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

        private static final long serialVersionUID = 1L;

        private final List<T> content;
        private final int pageNumber;
        private final int pageSize;
        private final long totalElements;

        public Page(List<T> content, int pageNumber, int pageSize, long totalElements) {
            if (pageNumber < 0) throw new IllegalArgumentException("Page number must be >= 0");
            if (pageSize < 1)   throw new IllegalArgumentException("Page size must be >= 1");
            if (totalElements < 0) throw new IllegalArgumentException("Total elements must be >= 0");
            this.content       = List.copyOf(Objects.requireNonNull(content, "Content cannot be null"));
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


