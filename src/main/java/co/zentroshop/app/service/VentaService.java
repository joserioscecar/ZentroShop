package co.zentroshop.app.service;

import co.zentroshop.app.entity.Producto;
import co.zentroshop.app.entity.Venta;
import co.zentroshop.app.excepctions.NegocioException;
import co.zentroshop.app.repository.ObjectRepository;
import java.io.IOException;
import java.lang.foreign.Linker.Option;
import java.util.List;
import java.util.Optional;

public class VentaService {

    private final ObjectRepository<Venta> ventaRepository;
    private final ObjectRepository<Producto> productoRepository;

    public VentaService() {

        ventaRepository = new ObjectRepository<>("data/ventas.dat");
        productoRepository = new ObjectRepository<>("data/producto.dat");
    }

    private int getNumeroUltimaVenta() {

        try {

            if (!ventaRepository.getAll().isEmpty()) {
                return ventaRepository.getAll().getLast().getNumero();
            }

        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(VentaService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return 1000;
    }

    public boolean realizarVenta(int skuProducto, int cantidad) throws NegocioException {

        Producto productoEncontrado = null;

        try {

            Optional<Producto> productoOp = productoRepository.find(p -> p.getSku() == skuProducto);

            if (productoOp.isPresent()) {

                productoEncontrado = productoOp.get();
                productoEncontrado.removerDelStock(cantidad);
                productoRepository.update(productoEncontrado);

                int numeroUltimaVenta = getNumeroUltimaVenta() + 1;
                Venta venta = new Venta(numeroUltimaVenta, productoEncontrado, cantidad);

                return ventaRepository.add(venta);
            }

        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(VentaService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        return false;
    }

    public List<Venta> consultarVentas() {
        try {
            return ventaRepository.getAll();
        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(VentaService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return null;
    }

}
