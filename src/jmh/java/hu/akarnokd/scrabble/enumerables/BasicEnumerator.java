package hu.akarnokd.scrabble.enumerables;

abstract class BasicEnumerator<T> implements IEnumerator<T> {

    protected T value;

    @Override
    public final T current() {
        return value;
    }
}
