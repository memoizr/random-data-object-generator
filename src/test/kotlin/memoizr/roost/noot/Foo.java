package memoizr.roost.noot;
import org.junit.Test;

public class Foo {

    @Test
    public void foo() {
        System.out.println("noo");

//        Integer[] x = (Integer[]) new Object[3];
        Integer[] y = this.y(Integer.class);
        y[0] = 10;
        System.out.println(y);
    }

    public <T> T[] y(Class<T> klass) {
        return (T[]) new Object[10];
    }

    interface Bar {
        void x() throws IllegalAccessError;
    }
}

