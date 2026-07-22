package hu.akarnokd.scrabble.enumerables;

public interface IEnumerator<T> {

    boolean moveNext();

    T current();
}
