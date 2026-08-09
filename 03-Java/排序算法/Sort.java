public class Sort {

    public static void main(String[] args) {
        int[] arr = {12, 3, 45, 23, 9, 8, 15, 27, 6, 18, 30, 1};
        System.out.println("排序前：");
        print(arr);

        //heapSort(arr);
        //System.out.println("堆排序后：");
        //print(arr);

//        quickSort(arr,0,arr.length-1);
//        System.out.println("快速排序后 ：");
//        print(arr);

//        mergeSort(arr);
//        System.out.println("归并排序后 ：");
//        print(arr);

        insertSort(arr);
        System.out.println("插入排序后 ：");
        print(arr);
    }

    // 打印数组
    private static void print(int[] arr){
        for (int num : arr){
            System.out.print(num + " ");
        }
        System.out.println();
    }

    // 堆排序 START

    private static void swap(int[] arr,int i,int j){
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    private static void heapify(int[] arr, int n, int i){
        int largest = i;
        int l = 2 * i + 1;
        int r = 2 * i + 2;
        if (l < n && arr[l] > arr[largest]){
            largest = l;
        }
        if (r < n && arr[r] > arr[largest]){
            largest = r;
        }
        if (largest != i){
            swap(arr,largest,i);
            heapify(arr,n,largest);
        }
    }

    private static void heapSort(int[] arr){
        int n = arr.length;

        for(int i=n/2-1;i>=0;i--){
            heapify(arr,n,i);
        }
        for(int i=n-1;i>0;i--){
            swap(arr,0,i);
            heapify(arr,i,0);
        }
    }

    // 堆排序 END

    // 快排 START

    private static void quickSort(int[] arr,int low,int high){
        if(low<high){
            int pi=partition(arr,low,high);
            quickSort(arr,low,pi-1);
            quickSort(arr,pi+1,high);
        }
    }

    private static int partition(int[] arr,int low,int high){
        int pivot=arr[high];
        int i=low-1;
        for(int j=low;j<high;j++){
            if(arr[j]<=pivot){
                i++;
                swap(arr,i,j);
            }
        }
        swap(arr,i+1,high);
        return i+1;
    }
	
	// 快排 END

    // 归并排序 START
    private static void mergeSort(int[] arr){
        if(arr==null||arr.length==1) return;
        int n=arr.length;
        int[] tem=new int[n];
        mSort(arr,0,n-1,tem);
    }

    private static void mSort(int[] arr,int left,int right,int[] tem){
        if(left<right){
            int mid=(right-left)/2+left;
            mSort(arr,left,mid,tem);
            mSort(arr,mid+1,right,tem);
            merge(arr,left,mid,right,tem);
        }
    }

    private static void merge(int[] arr,int left,int mid,int right,int[] tem){
        int i=left;
        int j=mid+1;
        int t=0;
        while(i<=mid&&j<=right){
            if(arr[i]>arr[j]) tem[t++]=arr[j++];
            else tem[t++]=arr[i++];
        }
        while(i<=mid) tem[t++]=arr[i++];
        while(j<=right) tem[t++]=arr[j++];
        t=0;
        int index=left;
        while (index<=right) arr[index++]=tem[t++];
    }
    // 归并排序 END

    // 插入排序 START
    private static void insertSort(int[] arr){
        int n=arr.length;
        for(int i=1;i<n;i++){
            int key=arr[i];
            int j=i-1;
            while(j>=0&&arr[j]>key){
                // 相当于把元素往右挪，给key腾出来地方
                arr[j+1]=arr[j];
                j--;
            }
            arr[j+1]=key;
        }
    }
    // 插入排序 END



}