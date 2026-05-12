package co.zentroshop.app.service;

import co.zentroshop.app.entity.Producto;
import co.zentroshop.app.entity.Venta;
import co.zentroshop.app.excepctions.NegocioException;
import co.zentroshop.app.repository.ObjectRepository;
import co.zentroshop.app.repository.ProductRepository;
import co.zentroshop.app.repository.VentaRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class VentaService {

    private final  ProductRepository productoRepository;
    private final VentaRepository ventaRepository;


    public VentaService() {

        ventaRepository = new VentaRepository();
        productoRepository = new ProductRepository();
    }

    private int getNumeroUltimaVenta() {

        if (!ventaRepository.getAll().isEmpty()) {
            return ventaRepository.getAll().getLast().getNumero();
        }
        return 1000;
    }

    public boolean realizarVenta(int skuProducto, int cantidad) throws NegocioException {

        Producto productoEncontrado = null;

        Optional<Producto> productoOp = productoRepository.findById(skuProducto);
        if (productoOp.isPresent()) {
            
            productoEncontrado = productoOp.get();
            productoEncontrado.removerDelStock(cantidad);
            productoRepository.update(productoEncontrado);
            
            int numeroUltimaVenta = getNumeroUltimaVenta() + 1;
            Venta venta = new Venta(numeroUltimaVenta, productoEncontrado, cantidad);
            
            return ventaRepository.save(venta);
        }

        return false;
    }

    public List<Venta> consultarVentas() {
        return ventaRepository.getAll();

    }

}
