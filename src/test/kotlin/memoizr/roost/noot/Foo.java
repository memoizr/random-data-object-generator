package memoizr.roost.noot;
import org.junit.Test;

public class Foo {

    @Test
    public void foo() {


    }

    public <T> T[] y(Class<T> klass) {
        return (T[]) new Object[10];
    }

    interface Bar {
        void x() throws IllegalAccessError;
    }
}

