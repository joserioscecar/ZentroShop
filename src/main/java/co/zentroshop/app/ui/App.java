package co.zentroshop.app.ui;

import co.zentroshop.app.entity.Producto;
import co.zentroshop.app.entity.Venta;
import co.zentroshop.app.excepctions.NegocioException;
import co.zentroshop.app.service.ProductoService;
import co.zentroshop.app.service.VentaService;
import java.util.List;
import java.util.Scanner;

public class App {

    static Scanner scanner = new Scanner(System.in);
    static ProductoService servicioProducto = new ProductoService();
    static VentaService servicioVenta = new VentaService();

    public static void main(String[] args) {
        int opcion;
        do {
            System.out.println("\n--- MENU PRINCIPAL ---");
            System.out.println("1. Registrar Producto");
            System.out.println("2. Eliminar Producto");
            System.out.println("3. Consultar Producto");
            System.out.println("4. Listar Productos");
            System.out.println("5. Realizar Compra");
            System.out.println("6. Consultar Ventas");
            System.out.println("7. Agregar Stock a un Producto");
            System.out.println("0. Salir");
            System.out.print("Seleccione una opcion: ");
            opcion = Integer.parseInt(scanner.nextLine().trim());

            switch (opcion) {
                case 1 -> registrarProducto();
                case 2 -> eliminarProducto();
                case 3 -> consultarProducto();
                case 4 -> listarProductos();
                case 5 -> realizarCompra();
                case 6 -> consultarVentas();
                case 7 -> agregarStock();
                case 0 -> System.out.println("Gracias por usar el sistema.");
                default -> System.out.println("Opcion invalida.");
            }
        } while (opcion != 0);
    }

    static void registrarProducto() {
        System.out.print("Nombre del producto: ");
        String nombre = scanner.nextLine();
        System.out.print("Precio: ");
        double precio = Double.parseDouble(scanner.nextLine().trim());
        System.out.print("Stock: ");
        int stock = Integer.parseInt(scanner.nextLine().trim());

        if (servicioProducto.registrarProducto(nombre, precio, stock)) {
            System.out.println("Producto registrado con exito.");
        } else {
            System.out.println("Datos invalidos. Precio >= 0 y stock > 0.");
        }
    }

    static void eliminarProducto() {
        System.out.print("Ingrese SKU del producto a eliminar: ");
        int sku = Integer.parseInt(scanner.nextLine().trim());
        if (servicioProducto.eliminarProducto(sku)) {
            System.out.println("Producto eliminado.");
        } else {
            System.out.println("Producto no encontrado.");
        }
    }

    static void consultarProducto() {
        System.out.print("Ingrese SKU del producto: ");
        int sku = Integer.parseInt(scanner.nextLine().trim());
        var p = servicioProducto.consultarProducto(sku);
        System.out.println(p.isPresent() ? p.get() : "Producto no encontrado.");
    }

    static void listarProductos() {
        List<Producto> lista = servicioProducto.listarProductos();
        if (lista.isEmpty()) {
            System.out.println("No hay productos registrados.");
        } else {
            lista.forEach(System.out::println);
        }
    }

    static void realizarCompra() {
        if (servicioProducto.listarProductos().isEmpty()) {
            System.out.println("No hay productos registrados.");
            return;
        }

        listarProductos();
        System.out.print("Ingrese SKU del producto: ");
        int sku = Integer.parseInt(scanner.nextLine().trim());
        var productoOp = servicioProducto.consultarProducto(sku);

        if (productoOp.isEmpty()) {
            System.out.println("Producto no encontrado.");
            return;
        }

        System.out.print("Cantidad a comprar: ");
        int cantidad = Integer.parseInt(scanner.nextLine().trim());

        try {
            if (servicioVenta.realizarVenta(productoOp.get(), cantidad)) {
                System.out.println("Compra realizada con exito.");
            } else {
                System.out.println("No se pudo realizar la compra.");
            }
        } catch (NegocioException ex) {
            System.err.println(ex.getMessage());
        }
    }

    static void consultarVentas() {
        List<Venta> lista = servicioVenta.consultarVentas();
        if (lista == null || lista.isEmpty()) {
            System.out.println("No hay ventas registradas.");
        } else {
            lista.forEach(System.out::println);
        }
    }

    static void agregarStock() {
        listarProductos();
        System.out.print("Ingrese SKU del producto: ");
        int sku = Integer.parseInt(scanner.nextLine().trim());
        System.out.print("Cantidad a agregar: ");
        int cantidad = Integer.parseInt(scanner.nextLine().trim());

        try {
            if (servicioProducto.agregarStock(sku, cantidad)) {
                System.out.println("Stock actualizado.");
            } else {
                System.out.println("No se pudo actualizar el stock.");
            }
        } catch (NegocioException ex) {
            System.getLogger(App.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }
}