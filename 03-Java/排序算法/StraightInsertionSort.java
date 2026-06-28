import java.util.Arrays;

//直接插入排序
public class StraightInsertionSort {

  public static void main(String[] args) {
    int[] arr = { 4, 5, 7, 1, 2, 9, 3, 6, 8, 0 };
    int len = arr.length;
    // i为当前要排序元素下标，j为遍历有序部分时的元素下标
    int i, j;
    for (i = 1; i < len; i++) {
      // 检验是否已经就是有序
      if (arr[i] < arr[i - 1]) {
        int tem = arr[i];
        // arr[j]>tem同样是为了检验是否当前已经有序，有序则退出循环
        for (j = i - 1; j >= 0 && arr[j] > tem; j--) {
          arr[j + 1] = arr[j];// 向后移动
        }
        arr[j + 1] = tem;
      }
    }
    // 直接打印是哈希码
    System.out.print(Arrays.toString(arr));

  }
}