package lrucache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Strictly bounded LRU with optional TTL and eviction observation. */
public final class LruCache<K,V> {
    public interface EvictionListener<K,V> { void evicted(K key, V value, boolean expired); }
    private static final class Node<K,V> { final K key; V value; Instant expiresAt; Node<K,V> previous,next; Node(K key,V value,Instant expiresAt){this.key=key;this.value=value;this.expiresAt=expiresAt;} }
    private final int capacity; private final Clock clock; private final EvictionListener<K,V> listener;
    private final java.util.LinkedHashMap<K,Node<K,V>> index; private final Node<K,V> head=new Node<>(null,null,null), tail=new Node<>(null,null,null);
    public LruCache(int capacity, Clock clock, EvictionListener<K,V> listener) { if(capacity<=0) throw new IllegalArgumentException("Capacity must be positive"); this.capacity=capacity; this.clock=Objects.requireNonNull(clock); this.listener=Objects.requireNonNull(listener); index=new java.util.LinkedHashMap<>(16,0.75f,true); head.next=tail; tail.previous=head; }
    public synchronized Optional<V> get(K key) { return get(key, clock.instant()); }
    public synchronized Optional<V> get(K key, Instant now) { Node<K,V> node=index.get(key); if(node==null) return Optional.empty(); if(isExpired(node,now)){remove(node,true); return Optional.empty();} moveToFront(node); return Optional.of(node.value); }
    public synchronized void put(K key,V value,Duration ttl) { Objects.requireNonNull(key); Objects.requireNonNull(value); Node<K,V> node=new Node<>(key,value,clock.instant().plus(ttl)); Node<K,V> old=index.put(key,node); if(old!=null) removeLinks(old); node.previous=head; node.next=head.next; head.next.previous=node; head.next=node; while(index.size()>capacity){Node<K,V> lru=tail.previous; remove(lru,false);} }
    public synchronized int size(Instant now) { index.values().removeIf(n->isExpired(n,now)); return index.size(); }
    private boolean isExpired(Node<K,V> n,Instant now){return n.expiresAt!=null&&!n.expiresAt.isAfter(now);}
    private void remove(Node<K,V> n,boolean expired){removeLinks(n);index.remove(n.key);listener.evicted(n.key,n.value,expired);}
    private void removeLinks(Node<K,V> n){n.previous.next=n.next;n.next.previous=n.previous;}
    private void moveToFront(Node<K,V> n){removeLinks(n);n.previous=head;n.next=head.next;head.next.previous=n;head.next=n;}
    public static void main(String[] args) { Clock clock=Clock.systemUTC(); LruCache<String,Integer> cache=new LruCache<>(2,clock,(k,v,e)->System.out.println("evicted="+k)); cache.put("a",1,Duration.ofMinutes(1));cache.put("b",2,Duration.ofMinutes(1));cache.get("a");cache.put("c",3,Duration.ofMinutes(1));System.out.println("size="+cache.size(clock.instant())+",a="+cache.get("a",clock.instant()).orElse(-1)+",b="+cache.get("b",clock.instant()).orElse(-1)); }
}
