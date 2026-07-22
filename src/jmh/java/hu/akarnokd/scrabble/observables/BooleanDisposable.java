package hu.akarnokd.scrabble.observables;

public final class BooleanDisposable implements IDisposable {

    boolean disposed;

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
