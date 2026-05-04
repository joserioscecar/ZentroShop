package co.zentroshop.app.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Venta implements Serializable{
    
    private final int numero;
    private final Producto producto;
    private final int cantidad;
    private final double total;
    private final double subTotal;
    private final double impuesto;

    private final LocalDateTime fecha;
    
    final static double IVA = 0.19;

    public Venta(int numero, Producto producto, int cantidad) {
        this.numero = numero;
        this.producto = producto;
        this.cantidad = cantidad;
        this.subTotal = producto.getPrecio() * cantidad;
        impuesto=subTotal*IVA;
        total = subTotal+impuesto;
        this.fecha = LocalDateTime.now();
    }

    public int getNumero() {
        return numero;
    }

    public double getSubTotal() {
        return subTotal;
    }
    
    public Producto getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public double getTotal() {
        return total;
    }
    
    public LocalDateTime getFecha() {
        return fecha;
    }
    
    public double getImpuesto() {
        return impuesto;
    }

    @Override
    public String toString() {
        return "Venta #" + numero + " | Fecha: "+getFecha()+""+" | Producto: " + producto.getNombre() +
               " | Cantidad: " + cantidad + " | Total: $" + String.format("%,.0f", total);

    }
    
    
    
}