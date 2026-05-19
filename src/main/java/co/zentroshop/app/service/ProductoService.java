package co.zentroshop.app.service;

import co.zentroshop.app.entity.Producto;
import co.zentroshop.app.enumeration.EstadoProducto;
import co.zentroshop.app.excepctions.NegocioException;
import co.zentroshop.app.repository.ProductRepository;
import java.util.List;
import java.util.Optional;

public class ProductoService {

    private final  ProductRepository productoRepository;

    public ProductoService() {
        productoRepository = new ProductRepository();
    }

    public boolean nombreDisponible(String nombre) {

        return productoRepository.find(p -> p.getNombre().equalsIgnoreCase(nombre)).isEmpty();

    }

    public boolean registrarProducto(Producto producto) {

        if (!nombreDisponible(producto.getNombre())) {

            System.err.println("Ya existe un producto registrado con el nombre " + producto.getNombre());

            return false;
        }

        return productoRepository.save(producto);


    }

    public boolean eliminarProducto(int sku) {

       return productoRepository.deleteById(sku);


    }

    public Optional<Producto> consultarProducto(int sku) {

        return productoRepository.findById(sku);

    }

    public List<Producto> listarProductos() {
        return productoRepository.filter(p -> p.getEstado() == EstadoProducto.DISPONIBLE).get();

    }

    public boolean agregarStock(int id, int cantidad) throws NegocioException, ClassNotFoundException {

        Optional<Producto> productoOp = consultarProducto(id);

        if (productoOp.isPresent()) {

            Producto producto = productoOp.get();
            producto.agregarAlStock(cantidad);
            productoRepository.update(producto); // TODO Auto-generated catch block

            return true;
        }

        return false;
    }

}
