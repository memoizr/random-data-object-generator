package memoizr.roost;

public class AGenericJavaClass<T> {
    T t;
    public  AGenericJavaClass(T t) {
        this.t = t;
    }

    public T getT() {
        return t;
    }
}
