package jaec;

/**
 * this class implement a FIFO array with pointer. this can increase the speed of operations.
 * also overlap-save is applied in this implementation. 
 * @author Jie Yang (Dublin, Januray, 2013)
 */
public class XFIFOArray {

    protected float[] array;
    protected int arrayLength;
    // use pointer to remove the cost of massive operation of array copying.
    protected int nextPointer;
    private int tailLength;
    private boolean sumSquareInitialized = false;
    //For optimisation purpose: do not need to re-calculate sharing parts between two signal process.
    private float preSumSquare;

    /**
     * the constructor of the class.
     *
     * @param length : the length of the array.
     */
    public XFIFOArray(int frameSize, int tailLength) {
        this.tailLength = tailLength;
        //overlap save 
        this.arrayLength = frameSize/2 + this.tailLength;
        this.array = new float[this.arrayLength];
        this.nextPointer = 0;
    }

    public void reset() {
        this.sumSquareInitialized = false;
        this.nextPointer = 0;
        this.array = new float[this.arrayLength];
    }

    /**
     * add new element at the end of the array, replaced the oldest one.
     *
     * @param a : the new element.
     */
    public void add(float a) {

        this.array[this.nextPointer] = a;
        this.nextPointer++;
        //"if" is more efficient than "%" 
        if (this.nextPointer == this.arrayLength) {
            this.nextPointer = 0;
        }
    }

    /**
     * return a element on certain position in the array.
     *
     * @param i : the location of the element. index 0 means the earliest signal
     * put into the array
     * @return
     */
    public float getElement(int i) {
        int po = (this.nextPointer + i);
        if (po >= this.arrayLength) {
            po = po - this.arrayLength;
        }
        return this.array[po];
    }

    /**
     * return the sum square of the array.
     *
     * @return : the sum square of the array.
     */
    public float getTailSumSquare(int indexInFrame) {
        float sumSquare = 0f;
        if (!this.sumSquareInitialized) {
            //for the first time calling, compute sum as below, then apply optimization method to increase speed.
            for (int i = 0; i < this.tailLength; i++) {
                float a = this.getElement(this.tailLength + indexInFrame - i);
                sumSquare += a * a;
            }
            this.preSumSquare = sumSquare;
            this.sumSquareInitialized = true;
            System.out.println("###SumSquare Initialized!");
        } else {
            //optimization to avoid massive calculation of sum square.
            float a0 = this.getElement(indexInFrame);
            float a1 = this.getElement(this.tailLength + indexInFrame);
            sumSquare = this.preSumSquare - a0 * a0 + a1 * a1;
            this.preSumSquare = sumSquare;
        }

        return sumSquare;
    }
}
