package co.zentroshop.app.service;

import co.zentroshop.app.entity.Producto;
import co.zentroshop.app.enumeration.EstadoProducto;
import co.zentroshop.app.excepctions.NegocioException;
import co.zentroshop.app.repository.ObjectRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ProductoService {

    private final ObjectRepository<Producto> productoRepository;

    public ProductoService() {
        productoRepository = new ObjectRepository("data/producto.dat");
    }

    public boolean nombreDisponible(String nombre) {

        try {
            return productoRepository.find(p -> p.getNombre().equalsIgnoreCase(nombre)).isEmpty();

        } catch (IOException | ClassNotFoundException ex) {

            System.getLogger(ProductoService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);

        }
        return false;
    }

    public boolean registrarProducto(Producto producto) {

        if (!nombreDisponible(producto.getNombre())) {

            System.err.println("Ya existe un producto registrado con el nombre " + producto.getNombre());

            return false;
        }

        try {

            return productoRepository.save(producto);

        } catch (IOException | ClassNotFoundException ex) {

            System.err.println(ex.getMessage());
        }

        return true;
    }

    public boolean eliminarProducto(int sku) {

        try {

            Producto producto = productoRepository.find(p -> p.getSku() == sku).get();
            return productoRepository.remove(producto);

        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(ProductoService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        return false;
    }

    public Optional<Producto> consultarProducto(int sku) {

        try {
            return productoRepository.find(p -> p.getSku() == sku);
        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(ProductoService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return Optional.empty();
    }

    public List<Producto> listarProductos() {
        try {
            return productoRepository.filter(p -> p.getEstado() == EstadoProducto.DISPONIBLE).get();
        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(ProductoService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return null;
    }

    public boolean agregarStock(int id, int cantidad) throws NegocioException {

        Optional<Producto> productoOp = consultarProducto(id);

        if (productoOp.isPresent()) {

            Producto producto = productoOp.get();
            producto.agregarAlStock(cantidad);
            try {
                productoRepository.update(producto);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            return true;
        }

        return false;
    }

}
