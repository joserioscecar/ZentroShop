package co.zentroshop.app.repository;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Repositorio genérico inspirado en JPA, Hibernate y el patrón de
 * repositorios de Spring Data, con persistencia mediante serialización
 * de objetos en archivos binarios.
 *
 * <p>Las entidades recuperadas requieren una llamada a {@link #update} para
 * guardar sus cambios. Cada operación se coordina entre instancias y procesos
 * que usan esta clase y la misma ruta de archivo, mediante un archivo auxiliar
 * {@code .lock} que debe conservarse. No hay transacciones entre operaciones
 * o repositorios. El directorio debe permitir crear el archivo de bloqueo,
 * incluso para consultar datos, y soportar reemplazos atómicos al escribir.</p>
 *
 * @param <T>  Tipo de objetos que serán almacenados en el repositorio
 * @param <ID> Tipo del identificador único de cada objeto
 *
 * @author José David Ríos Pacheco
 *         <a href="https://www.linkedin.com/in/joseriospacheco/">LinkedIn</a>
 * @version 2
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


    private static final Map<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private final Path filePath;
    private final Path lockPath;
    private final Function<T, ID> idExtractor;

    protected ObjectRepository(String pathName, Function<T, ID> idExtractor) {
        if (pathName == null || pathName.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        Objects.requireNonNull(idExtractor, "ID extractor cannot be null");
        this.filePath = Paths.get(pathName).toAbsolutePath().normalize();
        if (filePath.getFileName() == null) {
            throw new IllegalArgumentException("File path must identify a file");
        }
        this.lockPath = filePath.resolveSibling(filePath.getFileName() + ".lock");
        this.idExtractor = idExtractor;
    }

    private ID extractId(T object) {
        ID id = idExtractor.apply(Objects.requireNonNull(object, "Object cannot be null"));
        return Objects.requireNonNull(id, "ID cannot be null");
    }

    // ── Persistencia interna ─────────────────────────────────────────────────

    public List<T> getAll() {
        return withFileLock(this::readAll);
    }

    private <R> R withFileLock(Supplier<R> operation) {
        // El bloqueo JVM evita OverlappingFileLockException entre instancias.
        synchronized (FILE_LOCKS.computeIfAbsent(filePath, ignored -> new Object())) {
            try {
                Files.createDirectories(filePath.getParent());
                // Se bloquea un archivo estable, no el archivo que será reemplazado.
                try (FileChannel channel = FileChannel.open(lockPath,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    return operation.get();
                }
            } catch (IOException e) {
                throw new StorageException("Failed to access storage: " + filePath, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<T> readAll() {
        InputStream input;
        try {
            input = Files.newInputStream(filePath);
        } catch (NoSuchFileException e) {
            return new ArrayList<>();
        } catch (IOException e) {
            throw new StorageException("Failed to read from file: " + filePath, e);
        }
        try (BufferedInputStream buffered = new BufferedInputStream(input)) {
            buffered.mark(1);
            if (buffered.read() == -1) {
                return new ArrayList<>(); // Compatibilidad con archivos de cero bytes.
            }
            buffered.reset();
            try (ObjectInputStream ois = new ObjectInputStream(buffered)) {
                Object stored = ois.readObject();
                if (!(stored instanceof List<?> entries)) {
                    throw new StorageException("Expected a list in file: " + filePath);
                }
                List<T> collection = new ArrayList<>(entries.size());
                Set<ID> ids = new HashSet<>();
                for (Object entry : entries) {
                    T entity;
                    ID id;
                    try {
                        entity = (T) entry;
                        id = extractId(entity);
                    } catch (RuntimeException e) {
                        throw new StorageException("Invalid entity in file: " + filePath, e);
                    }
                    if (!ids.add(id)) {
                        throw new StorageException("Duplicate ID in file: " + filePath);
                    }
                    collection.add(entity);
                }
                return collection;
            }
        } catch (IOException e) {
            throw new StorageException("Failed to read from file: " + filePath, e);
        } catch (ClassNotFoundException e) {
            throw new StorageException("Incompatible data format in file: " + filePath, e);
        }
    }

    private void persist(List<T> collection) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(filePath.getParent(), ".repository-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporaryFile, StandardOpenOption.WRITE);
                 ObjectOutputStream oos = new ObjectOutputStream(
                         new BufferedOutputStream(Channels.newOutputStream(channel)))) {
                oos.writeObject(collection);
                oos.flush();
                channel.force(true);
            }
            // Si el sistema no admite reemplazo atómico, se conserva el original.
            Files.move(temporaryFile, filePath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw new StorageException("Failed to write to file: " + filePath, e);
        }
    }

    // ── Guardar ──────────────────────────────────────────────────────────────

    /** Inserta una entidad; un ID existente provoca {@link DuplicateEntityException}. */
    public void save(T object) {
        Objects.requireNonNull(object, "Cannot add a null object");
        ID id = extractId(object);
        withFileLock(() -> {
            List<T> collection = readAll();
            boolean exists = collection.stream().anyMatch(e -> Objects.equals(extractId(e), id));
            if (exists) {
                throw new DuplicateEntityException(id);
            }
            collection.add(object);
            persist(collection);
            return null;
        });
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

        return withFileLock(() -> {
            List<T> collection = readAll();
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
            persist(collection);
            return new ArrayList<>(batch);
        });
    }

    // ── Buscar ───────────────────────────────────────────────────────────────

    public Optional<T> findById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        List<T> collection = getAll();
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
        validatePageRequest(pageNumber, pageSize);
        return paginate(getAll(), pageNumber, pageSize);
    }

    public Page<T> findAll(int pageNumber, int pageSize, Comparator<T> sort) {
        Objects.requireNonNull(sort, "Sort comparator cannot be null");
        validatePageRequest(pageNumber, pageSize);

        List<T> all = getAll();
        all.sort(sort);

        return paginate(all, pageNumber, pageSize);
    }

    private static void validatePageRequest(int pageNumber, int pageSize) {
        if (pageNumber < 0) throw new IllegalArgumentException("Page number must be >= 0");
        if (pageSize < 1)   throw new IllegalArgumentException("Page size must be >= 1");
    }

    private static <E> Page<E> paginate(List<E> all, int pageNumber, int pageSize) {
        long offset = (long) pageNumber * pageSize;
        List<E> content = offset >= all.size() ? List.of()
                : all.subList((int) offset, (int) Math.min(offset + pageSize, all.size()));
        return new Page<>(content, pageNumber, pageSize, all.size());
    }

    public long count() {
        return getAll().size();
    }

    public Optional<T> find(Predicate<? super T> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Predicate cannot be null");
        }
        List<T> collection = getAll();
        for (T element : collection) {
            if (predicate.test(element)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    // ── Actualizar ───────────────────────────────────────────────────────────

    /** Reemplaza una entidad existente; falla con {@link EntityNotFoundException} si no existe. */
    public void update(T object) {
        Objects.requireNonNull(object, "Object cannot be null");
        ID id = extractId(object);
        withFileLock(() -> {
            List<T> collection = readAll();
            for (int i = 0; i < collection.size(); i++) {
                if (Objects.equals(extractId(collection.get(i)), id)) {
                    collection.set(i, object);
                    persist(collection);
                    return null;
                }
            }
            throw new EntityNotFoundException(id);
        });
    }

    /** Elimina por ID y devuelve {@code false} si la entidad no existe. */
    public boolean deleteById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");
        return withFileLock(() -> {
            List<T> collection = readAll();
            boolean removed = collection.removeIf(e -> Objects.equals(extractId(e), id));
            if (removed) {
                persist(collection);
            }
            return removed;
        });
    }

    /** Elimina la entidad o lanza {@link EntityNotFoundException} si no existe. */
    public void delete(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        ID id = extractId(entity);
        if (!deleteById(id)) {
            throw new EntityNotFoundException(id);
        }
    }

    /** Valida que todo el lote exista antes de eliminarlo; falla si falta un ID. */
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

        withFileLock(() -> {
            List<T> collection = readAll();
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
            persist(collection);
            return null;
        });
    }

    public void deleteAll() {
        withFileLock(() -> {
            persist(new ArrayList<>());
            return null;
        });
    }

    // ── Filtros ──────────────────────────────────────────────────────────────

    /** Crea un filtro mutable sobre los datos cargados en este momento. */
    public FilterBuilder<T> filter(Predicate<? super T> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Filter predicate cannot be null");
        }
        return new FilterBuilder<>(getAll(), predicate);
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
            if (maxSize == 0) return result;
            for (T element : collection) {
                if (predicate.test(element)) {
                    result.add(element);
                    if (result.size() >= maxSize) {
                        break;
                    }
                }
            }
            return result;
        }

        public Page<T> page(int pageNumber, int pageSize) {
            validatePageRequest(pageNumber, pageSize);
            return paginate(get(), pageNumber, pageSize);
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
            Objects.requireNonNull(mapper, "Mapper cannot be null");
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
        /** Devuelve el total de páginas, limitado al máximo representable por int. */
        public int getTotalPages()     { return (int) Math.min(totalPageCount(), Integer.MAX_VALUE); }
        public boolean hasNext()       {
            return pageNumber < Integer.MAX_VALUE && (long) pageNumber + 1 < totalPageCount();
        }
        public boolean hasPrevious()   { return pageNumber > 0; }
        public boolean isEmpty()       { return content.isEmpty(); }

        private long totalPageCount() {
            return totalElements / pageSize + (totalElements % pageSize == 0 ? 0 : 1);
        }

        @Override
        public String toString() {
            return String.format("Page %d of %d (total: %d)", (long) pageNumber + 1, totalPageCount(), totalElements);
        }
    }
}


