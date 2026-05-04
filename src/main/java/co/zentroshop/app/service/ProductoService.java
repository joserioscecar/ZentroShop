package co.zentroshop.app.service;

import co.zentroshop.app.entity.Producto;
import co.zentroshop.app.enumeration.EstadoProducto;
import co.zentroshop.app.excepctions.NegocioException;
import co.zentroshop.app.repository.ObjectRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ProductoService {


    private final ObjectRepository<Producto> productoRepository ;

    public ProductoService() {
        productoRepository = new ObjectRepository("data/producto.dat");
    }

    public boolean nombreDisponible(String nombre) {
        
        try {
            return productoRepository.find(p->p.getNombre().equalsIgnoreCase(nombre)).isEmpty();
            
        } catch (IOException | ClassNotFoundException ex) {
            
            ex.printStackTrace();
            System.getLogger(ProductoService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            
        }
        return false;
    }

    public boolean registrarProducto(String nombre, double precio, int stock) {

        
        if(!nombreDisponible(nombre)) {
            
             System.err.println("Ya existe un producto registrado con el nombre "+nombre);
            
            return  false;
        }
        
        
        try {
            Producto nuevo = new Producto(nombre, precio, stock);
            
            return productoRepository.add(nuevo);
            
               
        } catch (NegocioException | IOException | ClassNotFoundException  ex) {
            
            ex.printStackTrace();
            System.err.println(ex.getMessage());
        }

        return true;
    }

    public boolean eliminarProducto(int sku) {
        
        try {
            
            int indice = productoRepository.indexOf(p->p.getSku()==sku);
            return productoRepository.remove(indice);
            
        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(ProductoService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
       

        return false; 
    }

    public  Optional<Producto> consultarProducto(int sku) {
                
        try {
            return productoRepository.find(p->p.getSku()==sku);
        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(ProductoService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return Optional.empty();
    }

    public List<Producto> listarProductos() {
        try {
            return productoRepository.filter(p->p.getEstado()==EstadoProducto.DISPONIBLE).get();
        } catch (IOException | ClassNotFoundException ex) {
            System.getLogger(ProductoService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return null;
    }

    public boolean agregarStock(int id, int cantidad) throws NegocioException {
        
        Optional<Producto> productoOp = consultarProducto(id);
         
        if(productoOp.isPresent()){
           
            Producto producto = productoOp.get();
            producto.agregarAlStock(cantidad);
            
            return  true;
        }

 
        return  false;
    }

}
