
package co.zentroshop.app.repository;

import co.zentroshop.app.entity.Producto;

public class ProductRepository extends ObjectRepository<Producto, Integer>{

    public ProductRepository() {
      super("data/sales.bin", Producto::getSku);
    }
     
    
    
}
