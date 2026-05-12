
package co.zentroshop.app.repository;

import co.zentroshop.app.entity.Venta;

public class VentaRepository extends ObjectRepository<Venta, Integer>{

    public VentaRepository() {
      super("data/sales.bin", Venta::getNumero);
    }
     
    
    
}
