public class HashMap<K,V>
    extends AbstractMap<K,V>
    implements Map<K,V>, Cloneable, Serializable
{

    /**
     * 初始数组容量
     * 2^4 = 16, 默认数组容量大小为 16。（使用 2 次方数即是为了保证 indexFor 的用法）
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;

    /**
     * 最大数组容量限制
     * 2 ^ 30 
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * 默认加载因子
     * 0.75，即 4/3，跟扩容有关
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     *一个空 Entry
     */
    static final Entry<?,?>[] EMPTY_TABLE = {};

    /**
     * table 
     */
    transient Entry<K,V>[] table = (Entry<K,V>[]) EMPTY_TABLE;

    /**
     * 数组容量
     */
    transient int size;

    /**
     * 阈值
     * 跟扩容有关，阈值 = 数组容量 * 加载因子
     */
    int threshold;

    /**
     * 加载因子
     */
    final float loadFactor;

    /**
     * 与并发有关
     */
    transient int modCount;


    /**
     * 自定义容量值和阈值构造方法
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);

        this.loadFactor = loadFactor;
        threshold = initialCapacity;
        init();
    }

    /**
     * 自定义初始化容量值构造方法
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 默认构造方法
     */
    public HashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    private static int roundUpToPowerOf2(int number) {
        // assert number >= 0 : "number must be non-negative";
        return number >= MAXIMUM_CAPACITY
                ? MAXIMUM_CAPACITY
                : (number > 1) ? Integer.highestOneBit((number - 1) << 1) : 1;
    }

    /**
     * 初始化方法.
     */
    private void inflateTable(int toSize) {
        // 找大于或等于初始化容量数的二次方数，也就是说如果传入的 toSize = 15 ，那么 capacity = 16.
        int capacity = roundUpToPowerOf2(toSize);

        // 计算阈值，阈值 = 容量 * 加载因子
        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
        table = new Entry[capacity];
        initHashSeedAsNeeded(capacity);
    }

    /**
     * hash 操作
     */
    final int hash(Object k) {
        int h = hashSeed;
        if (0 != h && k instanceof String) {
            return sun.misc.Hashing.stringHash32((String) k);
        }

        // 得到哈希码
        h ^= k.hashCode();

        // 右移和异或操作
        // 这是是为了增加散列性（让高位参与运算）
        // 比如 hashcode 算出来的为 0110 0111，向右移4位后变成 0000 0110，两两异或后再与原来的 hashcode 参与运算，算出的值即为最终的 hashcode，
        // 这样原来的 hashcode 高四位和第四位都参与运算了，增强了散列性。
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * 返回数组下标
     */
    static int indexFor(int h, int length) {
        // 与操作，之所以要减 1，是为了保证与操作 （length -1） 的低位都是1，这样与 hashcode 相与后取值范围才是符合要求的。
        // 奇数的话满足高位为 0，低位为 1，保证最大范围。如 15 ： 0000 1111。相与操作的时候后四位都将是hashcode的值。
        return h & (length-1);
    }

    /**
     * get 操作
     **/
    public V get(Object key) {
        if (key == null)
            return getForNullKey();
        Entry<K,V> entry = getEntry(key);

        return null == entry ? null : entry.getValue();
    }

    /**
     * 查询 key 的方法
     */
    final Entry<K,V> getEntry(Object key) {
        if (size == 0) {
            return null;
        }

        int hash = (key == null) ? 0 : hash(key);
        for (Entry<K,V> e = table[indexFor(hash, table.length)];
             e != null;
             e = e.next) {
            Object k;
            if (e.hash == hash &&
                ((k = e.key) == key || (key != null && key.equals(k))))
                return e;
        }
        return null;
    }

    /**
     * put 方法，添加元素
     */
    public V put(K key, V value) {
        // 判断是否为空
        if (table == EMPTY_TABLE) {
            // 为空进行初始化
            inflateTable(threshold);
        }
        // 可以看到 key 可以为空
        if (key == null)
            return putForNullKey(value);

        int hash = hash(key);
        int i = indexFor(hash, table.length);
        for (Entry<K,V> e = table[i]; e != null; e = e.next) {
            Object k;
            // 即 put 的 key 已经存在的话，那么将会覆盖旧值，，并返回旧的 value
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }

        modCount++;
        // 添加新的 entry 方法
        addEntry(hash, key, value, i);
        return null;
    }

    /**
     * 扩容方法
     */
    void resize(int newCapacity) {
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        // new 一个新的 Entry
        Entry[] newTable = new Entry[newCapacity];
        // 将老 table 的元素转移到新 table 的方法
        transfer(newTable, initHashSeedAsNeeded(newCapacity));
        table = newTable;
        // 重新计算阈值
        threshold = (int)Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
    }

    /**
     * 将旧 table 的数据转移到新 table
     */
    void transfer(Entry[] newTable, boolean rehash) {
        int newCapacity = newTable.length;
        // 双重循环：循环数组和链表，然后一个一个移动到新 table 中
        for (Entry<K,V> e : table) {
            while(null != e) {
                Entry<K,V> next = e.next;
                // 固定为 false，不会堆对元素重新进行 hash
                if (rehash) {
                    e.hash = null == e.key ? 0 : hash(e.key);
                }
                int i = indexFor(e.hash, newCapacity);
                e.next = newTable[i];
                newTable[i] = e;
                e = next;
            }
        }
    }

    /**
     * 将元素插入 hashMap（扩容判断）
     */
    void addEntry(int hash, K key, V value, int bucketIndex) {
        // hashmap 的数组大小大于或等于阈值 && 插入元素的位置不为空
        if ((size >= threshold) && (null != table[bucketIndex])) {
            // 扩容操作，当前数组大小的两倍
            resize(2 * table.length);
            hash = (null != key) ? hash(key) : 0;
            bucketIndex = indexFor(hash, table.length);
        }

        createEntry(hash, key, value, bucketIndex);
    }

    /**
     * 将元素插入 hashMap
     * 1. 插入到头节点，2. 移动头节点
     */
    void createEntry(int hash, K key, V value, int bucketIndex) {
        Entry<K,V> e = table[bucketIndex];
        table[bucketIndex] = new Entry<>(hash, key, value, e);
        size++;
    }
