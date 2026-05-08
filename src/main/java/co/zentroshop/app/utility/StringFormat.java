
package co.zentroshop.app.utility;

import java.text.NumberFormat;
import java.util.Currency;

public class StringFormat {
    
    public static String money(double valor) {
        NumberFormat nf = NumberFormat.getCurrencyInstance();
        nf.setCurrency(Currency.getInstance("COP"));
        return nf.format(valor);
           
    }
}
