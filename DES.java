package des;

import java.util.Arrays;

public class DES {

    public static void main(String[] args) {
        //{0xAA, 0xBB, 0x09, 0x18, 0x27, 0x36, 0xCC, 0xDD}; key
        //{0x12, 0x34, 0x56, 0xAB, 0xCD, 0x13, 0x25, 0x36}; in
        //{0xC0, 0xB7, 0xA8, 0xD0, 0x5F, 0x3A, 0x82, 0x9C}; out
        int[] output = calcDes(new int[]{0x12, 0x34, 0x56, 0xAB, 0xCD, 0x13, 0x25, 0x36}, getRoundKeys(1, doPermutation(new int[]{0xAA, 0xBB, 0x09, 0x18, 0x27, 0x36, 0xCC, 0xDD}, K58, 8)));
    }

    private static int[][] getRoundKeys(int type, int[] initKey) {
        //Делим на две половины, дальше работа именно с половинами (половина - одно большое число)
        int leftHalf = initKey[0];
        int rightHalf = initKey[1];
        //начальный поворот, крутим независимо левую и правую часть
        //кодировка - 0, раскодировка - 1!
        if (type == -1) {
            leftHalf = rotate28bit(1, leftHalf);
            rightHalf = rotate28bit(1, rightHalf);
        }
        int[][] rKeys = new int[16][2];
        for (int k = 0; k < 16; k++) {
            //крутим независимо левую и правую часть
            leftHalf = rotate28bit(SS[k] * type, leftHalf);
            rightHalf = rotate28bit(SS[k] * type, rightHalf);
            //склеиваем
            rKeys[k][0] = leftHalf;
            rKeys[k][1] = rightHalf;
            //сжимаем до 48
            rKeys[k] = doPermutation(rKeys[k], K48, 28);
        }
        return rKeys;
    }

    private static int rotate28bit(int rotLen, int num) {
        return (rotLen >= 0) ? RL(rotLen, num) : RR(-rotLen, num);
    }

    private static int RL(int rotLen, int num) {
        //Вылезшие за край биты
        int outBits = (num >> (28 - rotLen)) & (int) (Math.pow(2, rotLen) - 1);
        return (num << rotLen) | outBits;
    }

    private static int RR(int rotLen, int num) {
        //Вылезшие за край биты
        int outBits = (num & (int) (Math.pow(2, rotLen) - 1)) << (28 - rotLen);
        return outBits | (num >> rotLen);
    }

    private static int[] calcDes(int[] input, int[][] rKeys) {
        //64 бита - блок в DES => в блоке 8 !!!байт!!! (8 * 8) = 64
        //вычисляем необходимое кол-во блоков
        int countBlocks = ((input.length - 1) / 8 + 1);
        //Массив нужной длины кратной 8ми (64м)
        int[] formatInput = new int[countBlocks * 8];
        //Просто перекопируем в файл нужного размера
        System.arraycopy(input, 0, formatInput, 0, input.length);
        //Сколько входных, столько и выходных
        int[] output = new int[countBlocks * 8];
        //для каждых 8ми !!!байт!!! (64х бит) делаем преобразования
        for (int n = 0; n < countBlocks; n++) {
            int[] block = new int[8];
            System.arraycopy(formatInput, n * 8, block, 0, 8);
            //выполняем начальную перестановку блока и ключа
            block = doPermutation(block, MIP, 8);
            for (int k = 0; k < 15; k++) {
                //вычисляем новый правый полублок
                int[] newHalf = calcHalf(block, rKeys[k]);
                //склеиваем, при этом переставляя
                System.arraycopy(block, block.length / 2, block, 0, block.length / 2);
                System.arraycopy(newHalf, 0, block, block.length / 2, block.length / 2);
            }
            //последняя итерация выполняется немного по-другому
            //вычисляем новый правый полублок
            int[] newHalf = calcHalf(block, rKeys[15]);
            //заменяем левую часть не переставляя
            System.arraycopy(newHalf, 0, block, 0, block.length / 2);
            //выполняем конечную перестановку
            block = doPermutation(block, MTP, 8);
            //записываем блок в выходной массив
            System.arraycopy(block, 0, output, n * 8, 8);
        }
        return output;
    }

    private static int[] calcHalf(int[] block, int[] rKey) {
        int[] rightHalf = RH(block);
        //расширяем до 48ми
        rightHalf = doPermutation(rightHalf, B48, 8);
        for (int i = 0; i < rightHalf.length; i++) {
            //суммируем по модулю 2 с ключом 
            rightHalf[i] ^= rKey[i];
        }
        //прогоняем через S-боксы, тем сымым сжимаем до 32х
        for (int i = 0; i < block.length; i++) {
            rightHalf[i] = getBitSBoxs((rightHalf[i] & 0x3F), i);
        }
        //из полученной матрицы 4 на 8 формируем 8 на 4 (не просто транспонируем, а снова матрица перестановок)
        rightHalf = doPermutation(rightHalf, B32, 4);
        int[] leftHalf = LH(block);
        for (int i = 0; i < rightHalf.length; i++) {
            //суммируем по модулю 2 с левой половиной
            rightHalf[i] ^= leftHalf[i];
        }
        return rightHalf;
    }

    //Получить бит из S-боксов
    private static int getBitSBoxs(int b, int numByte) {
        //по алгоритму: первый и последний - строка, остальные - столбец
        int row = (((b >> 5) & 0x1) << 1) | (b & 0x1);
        int col = (((b >> 4) & 0x1) << 3) | (((b >> 3) & 0x1) << 2) | (((b >> 2) & 0x1) << 1) | ((b >> 1) & 0x1);
        //из S-блока с соответсвующим номеру байта номером
        return (SBoxs[numByte][row][col] & 0xF);
    }

    //Перестановка бит, согласно матрице
    private static int[] doPermutation(int[] block, int[][] MP, int countBit) {
        //здесь countBit - количество бит в каждом !!!байте!!! входящего блока
        int[] newBlock = new int[MP.length];
        for (int i = 0; i < MP.length; i++) {
            //инициализируем первым битом согласно матрице перестановок
            newBlock[i] = getBitNum(block, MP[i][0], countBit);
            //и 7 раз (так как осталось добавить ещё 7 бит)
            for (int j = 1; j < MP[i].length; j++) {
                //сдвигаем, освобождая место под новый
                newBlock[i] <<= 1;
                //добавляем новый бит
                newBlock[i] |= getBitNum(block, MP[i][j], countBit);
            }
        }
        return newBlock;
    }

    //Получить бит из блока с заданным номером
    private static int getBitNum(int[] block, int numBit, int countBit) {
        //здесь countBit - количество бит в байте
        //чтобы начинать с нуля
        numBit -= 1;
        //вычисляем позицию бита с заданным номером в массиве байт
        int row = numBit / countBit;
        int col = numBit % countBit;
        //взяли байт - сдвинули, откинув нужное количество бит - взяли последний
        return ((block[row] >> (countBit - col - 1)) & 0x1);
    }

    //Первая половина
    private static int[] LH(int[] block) {
        int[] LH = new int[block.length / 2];
        System.arraycopy(block, 0, LH, 0, block.length / 2);
        return LH;
    }

    //Вторая половина
    private static int[] RH(int[] block) {
        int[] RH = new int[block.length / 2];
        System.arraycopy(block, block.length / 2, RH, 0, block.length / 2);
        return RH;
    }

    //<editor-fold defaultstate="collapsed" desc="матрицы перестановок">
    //<editor-fold defaultstate="collapsed" desc="ключи">
    //Матрица начального сжатия и перестановки ключа (58)
    private static final int[][] K58 = {
        {57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 27, 19, 11, 3, 60, 52, 44, 36},
        {63, 55, 47, 39, 31, 23, 15, 7, 62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 28, 20, 12, 4}
    };
    //Величина сдвига в каждом раунде
    private static final int[] SS = {1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1};
    //Матрица сжатия ключа (48)
    private static final int[][] K48 = {
        {14, 17, 11, 24, 1, 5},
        {3, 28, 15, 6, 21, 10},
        {23, 19, 12, 4, 26, 8},
        {16, 7, 27, 20, 13, 2},
        {41, 52, 31, 37, 47, 55},
        {30, 40, 51, 45, 33, 48},
        {44, 49, 39, 56, 34, 53},
        {46, 42, 50, 36, 29, 32}
    };
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="блоки">
    //матрица расширения полублока до 48 бит
    private static final int[][] B48 = {
        {32, 1, 2, 3, 4, 5},
        {4, 5, 6, 7, 8, 9},
        {8, 9, 10, 11, 12, 13},
        {12, 13, 14, 15, 16, 17},
        {16, 17, 18, 19, 20, 21},
        {20, 21, 22, 23, 24, 25},
        {24, 25, 26, 27, 28, 29},
        {28, 29, 30, 31, 32, 1}
    };
    //S-боксы
    private static int[][][] SBoxs = {
        {
            {14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7},
            {0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8},
            {4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0},
            {15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13}
        }, {
            {15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10},
            {3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5},
            {0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15},
            {13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9}
        }, {
            {10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8},
            {13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1},
            {13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7},
            {1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12}
        }, {
            {7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15},
            {13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9},
            {10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4},
            {3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14}
        }, {
            {2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9},
            {14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6},
            {4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14},
            {11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3}
        }, {
            {12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11},
            {10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8},
            {9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6},
            {4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13}
        }, {
            {4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1},
            {13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6},
            {1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2},
            {6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12}
        }, {
            {13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7},
            {1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2},
            {7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8},
            {2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11}
        }
    };
    //Матрица сжатия полублока (32)
    private static final int[][] B32 = {
        {16, 7, 20, 21, 29, 12, 28, 17},
        {1, 15, 23, 26, 5, 18, 31, 10},
        {2, 8, 24, 14, 32, 27, 3, 9},
        {19, 13, 30, 6, 22, 11, 4, 25}
    };
    //</editor-fold>
    //Матрица начальных перестановок
    private static final int[][] MIP = {
        {58, 50, 42, 34, 26, 18, 10, 2},
        {60, 52, 44, 36, 28, 20, 12, 4},
        {62, 54, 46, 38, 30, 22, 14, 6},
        {64, 56, 48, 40, 32, 24, 16, 8},
        {57, 49, 41, 33, 25, 17, 9, 1},
        {59, 51, 43, 35, 27, 19, 11, 3},
        {61, 53, 45, 37, 29, 21, 13, 5},
        {63, 55, 47, 39, 31, 23, 15, 7}
    };

    //Матрица конечных перестановок
    private static final int[][] MTP = {
        {40, 8, 48, 16, 56, 24, 64, 32},
        {39, 7, 47, 15, 55, 23, 63, 31},
        {38, 6, 46, 14, 54, 22, 62, 30},
        {37, 5, 45, 13, 53, 21, 61, 29},
        {36, 4, 44, 12, 52, 20, 60, 28},
        {35, 3, 43, 11, 51, 19, 59, 27},
        {34, 2, 42, 10, 50, 18, 58, 26},
        {33, 1, 41, 9, 49, 17, 57, 25}
    };

    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="методы выведения переменных">
    private static String toBin(int num) {
        return Integer.toBinaryString(num);
    }

    private static String toHex(int num) {
        return Integer.toHexString(num).toUpperCase();
    }

    private static String addDelimer(String string, int position, String delimer) {
        return string.replaceAll("(.{" + position + "})", "$1" + delimer);
    }

    private static String trimToLen(String string, int length) {
        return String.format("%" + length + "s", string).replace(' ', '0');
    }
    //</editor-fold>
}
