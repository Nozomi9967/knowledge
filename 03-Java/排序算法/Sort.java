import java.util.Random;

public class Sort {

    private static final Random rand = new Random();

    public static void main(String[] args) {
        int[] arr = {12, 3, 45, 23, 9, 8, 15, 27, 6, 18, 30, 1};
        System.out.println("排序前：");
        print(arr);

        // 堆排序
        // heapSort(arr);
        // System.out.println("堆排序后：");
        // print(arr);

        // 快速排序（随机化三路快排）
        quickSort(arr, 0, arr.length - 1);
        System.out.println("快速排序后 ：");
        print(arr);

        // 归并排序
        // mergeSort(arr);
        // System.out.println("归并排序后 ：");
        // print(arr);

        // 插入排序
        // insertSort(arr);
        // System.out.println("插入排序后 ：");
        // print(arr);
    }

    // 打印数组
    private static void print(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
        System.out.println();
    }

    // 交换元素
    private static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    // ==================== 快速排序 START（随机化三路快排） ====================
    private static void quickSort(int[] arr, int low, int high) {
        if (low >= high) return;

        // 优化 1：随机基准值（防止有序数组退化为 O(N^2)）
        int randomIndex = low + rand.nextInt(high - low + 1);
        swap(arr, low, randomIndex);

        int pivot = arr[low];

        // 优化 2：三路切分（处理大量重复元素）
        // [low, lt-1]   < pivot
        // [lt, i-1]     == pivot
        // [gt+1, high]  > pivot
        int lt = low;      // 小于区的右边界
        int gt = high;     // 大于区的左边界
        int i = low + 1;   // 当前扫描的指针

        while (i <= gt) {
            if (arr[i] < pivot) {
                swap(arr, lt, i);
                lt++;
                i++;
            } else if (arr[i] > pivot) {
                swap(arr, i, gt);
                gt--; // 注意：从 gt 换过来的元素未经过检查，i 不自增
            } else {
                i++;  // 等于 pivot，直接跳过
            }
        }

        // 递归左半部分（小于区）和右半部分（大于区），等于区自动归位无需处理
        quickSort(arr, low, lt - 1);
        quickSort(arr, gt + 1, high);
    }
    // ==================== 快速排序 END ====================

    // ==================== 堆排序 START ====================
    private static void heapify(int[] arr, int n, int i) {
        int largest = i;
        int l = 2 * i + 1;
        int r = 2 * i + 2;
        if (l < n && arr[l] > arr[largest]) {
            largest = l;
        }
        if (r < n && arr[r] > arr[largest]) {
            largest = r;
        }
        if (largest != i) {
            swap(arr, largest, i);
            heapify(arr, n, largest);
        }
    }

    private static void heapSort(int[] arr) {
        int n = arr.length;

        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(arr, n, i);
        }
        for (int i = n - 1; i > 0; i--) {
            swap(arr, 0, i);
            heapify(arr, i, 0);
        }
    }
    // ==================== 堆排序 END ====================

    // ==================== 归并排序 START ====================
    private static void mergeSort(int[] arr) {
        if (arr == null || arr.length <= 1) return;
        int n = arr.length;
        int[] tem = new int[n];
        mSort(arr, 0, n - 1, tem);
    }

    private static void mSort(int[] arr, int left, int right, int[] tem) {
        if (left < right) {
            int mid = (right - left) / 2 + left;
            mSort(arr, left, mid, tem);
            mSort(arr, mid + 1, right, tem);
            merge(arr, left, mid, right, tem);
        }
    }

    private static void merge(int[] arr, int left, int mid, int right, int[] tem) {
        int i = left;
        int j = mid + 1;
        int t = 0;
        while (i <= mid && j <= right) {
            if (arr[i] > arr[j]) tem[t++] = arr[j++];
            else tem[t++] = arr[i++];
        }
        while (i <= mid) tem[t++] = arr[i++];
        while (j <= right) tem[t++] = arr[j++];
        t = 0;
        int index = left;
        while (index <= right) arr[index++] = tem[t++];
    }
    // ==================== 归并排序 END ====================

    // ==================== 插入排序 START ====================
    private static void insertSort(int[] arr) {
        int n = arr.length;
        for (int i = 1; i < n; i++) {
            int key = arr[i];
            int j = i - 1;
            while (j >= 0 && arr[j] > key) {
                // 相当于把元素往右挪，给key腾出来地方
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }
    // ==================== 插入排序 END ====================

    // shell排序 START
    public class ShellSort {

        public static void shellSort(int[] arr) {
            if (arr == null || arr.length < 2) {
                return;
            }

            // 增量逐步减半，最后一次增量为 1
            for (int gap = arr.length / 2; gap > 0; gap /= 2) {
                // 对每个 gap 分组进行插入排序
                for (int i = gap; i < arr.length; i++) {
                    int current = arr[i];
                    int j = i;

                    // 当前元素沿所在分组向前比较，必要时后移元素
                    while (j >= gap && arr[j - gap] > current) {
                        arr[j] = arr[j - gap];
                        j -= gap;
                    }

                    arr[j] = current;
                }
            }
        }

    // shell排序 END
}