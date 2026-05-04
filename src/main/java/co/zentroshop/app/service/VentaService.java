package co.zentroshop.app.service;

import co.zentroshop.app.entity.Producto;
import co.zentroshop.app.entity.Venta;
import co.zentroshop.app.excepctions.NegocioException;
import co.zentroshop.app.repository.ObjectRepository;
import java.io.IOException;
import java.util.List;

public class VentaService {

    private final ObjectRepository<Venta> ventaRepository;

    public VentaService() {

        ventaRepository = new ObjectRepository<>("data/ventas.dat");

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

    public boolean realizarVenta(Producto producto, int cantidad) throws NegocioException {
        if (producto != null) {
            try {
                producto.removerDelStock(cantidad);

                int numeroUltimaVenta = getNumeroUltimaVenta() + 1;

                Venta venta = new Venta(numeroUltimaVenta, producto, cantidad);

               return ventaRepository.add(venta);

            } catch (IOException | ClassNotFoundException ex) {
                System.getLogger(VentaService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }

        }
        return  false;
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
