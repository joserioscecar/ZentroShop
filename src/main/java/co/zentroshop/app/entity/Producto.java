package co.zentroshop.app.entity;

import co.zentroshop.app.enumeration.EstadoProducto;
import co.zentroshop.app.excepctions.NegocioException;
import java.io.Serializable;
import java.util.Random;

public class Producto  implements Serializable{

    private final int sku;
    private final String nombre;
    private double precio;
    private int stock;
    private EstadoProducto estado;
    final static double PRECIO_MININO = 500;

    public Producto(String nombre, double precio, int stock) throws NegocioException {

    
        sku = generarSku();
        validarPrecio(precio);
        this.nombre = nombre;
        this.precio = precio;
        this.stock = stock;
        this.estado = EstadoProducto.DISPONIBLE;
    }

    public String getNombre() {
        return nombre;
    }

    public double getPrecio() {
        return precio;
    }

    public void setPrecio(double precio) throws NegocioException {
        validarPrecio(precio);
         this.precio = precio;
    }

    public int getStock() {
        return stock;
    }

    public EstadoProducto getEstado() {
        return estado;
    }

    public void setEstado(EstadoProducto estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        return "SKU: " + sku + " | Nombre: " + nombre + " | Precio: $" + String.format("%,.0f", precio) + " | Stock: " + stock;

    }

    public void agregarAlStock(int stock) throws NegocioException {
        validarStock(stock);
        this.stock += stock;
    }

    
    public int getSku() {
        return sku;
    }
    
    public void removerDelStock(int stock) throws NegocioException {

        validarStock(stock);
        this.stock -= stock;
        if (this.stock == 0) {
            estado = EstadoProducto.AGOTADO;
        }
    }
        
    private void validarStock(double stock) throws NegocioException {

        if ( stock <= 0) {
            throw new NegocioException("El producto no cuenta con stock suficiente");
        }
    
    }

    private void validarPrecio(double precio) throws NegocioException {

        if (precio < PRECIO_MININO) {
            throw new NegocioException("El valor ingresado para el producto no es valido");
        }
   
    }
    
    public static int generarSku() {
        Random random = new Random();
        return 100000 + random.nextInt(900000);
    }

    @Override
    public boolean equals(Object obj) {
        
        Producto p = (Producto) obj;
               
        return sku == p.sku;
    }

    @Override
    public int hashCode() {
        return sku;
    }
    
   
    
}
