package dev.afkmod.testlab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Keeps the last {@code capacity} items, oldest first. Not thread safe (client thread only). */
public final class RingBuffer<T> {
	private final int capacity;
	private final Deque<T> items = new ArrayDeque<>();

	public RingBuffer(int capacity) {
		if (capacity < 1) throw new IllegalArgumentException("capacity must be at least 1");
		this.capacity = capacity;
	}

	public void add(T item) {
		items.addLast(item);
		while (items.size() > capacity) items.removeFirst();
	}

	/** Every item, oldest first. */
	public List<T> all() {
		return List.copyOf(items);
	}

	/** The last {@code n} items, oldest first. */
	public List<T> last(int n) {
		if (n <= 0) return List.of();
		List<T> all = new ArrayList<>(items);
		return List.copyOf(all.subList(Math.max(0, all.size() - n), all.size()));
	}

	public int size() {
		return items.size();
	}

	public int capacity() {
		return capacity;
	}

	public void clear() {
		items.clear();
	}
}
