package com.sojourners.chess.util;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManualConverter {
    /**
     * 棋子映射关系
     */
    public static Map<String, Character> map = new HashMap<>(32);
    public static Map<String, Integer> NUMBER_MAP = new HashMap<>(32);

    public static List<String> INDEX_LIST = new ArrayList<>();
    static {
        map.put("车",'r');
        map.put("马",'n');
        map.put("炮",'c');

        map.put("象",'b');
        map.put("士",'a');
        map.put("将",'k');
        map.put("卒",'p');

        map.put("相",'B');
        map.put("仕",'A');
        map.put("帅",'K');
        map.put("兵",'P');

        NUMBER_MAP.put("一",1);
        NUMBER_MAP.put("二",2);
        NUMBER_MAP.put("三",3);
        NUMBER_MAP.put("四",4);
        NUMBER_MAP.put("五",5);
        NUMBER_MAP.put("六",6);
        NUMBER_MAP.put("七",7);
        NUMBER_MAP.put("八",8);
        NUMBER_MAP.put("九",9);

        INDEX_LIST.add("a");
        INDEX_LIST.add("b");
        INDEX_LIST.add("c");
        INDEX_LIST.add("d");
        INDEX_LIST.add("e");
        INDEX_LIST.add("f");
        INDEX_LIST.add("g");
        INDEX_LIST.add("h");
        INDEX_LIST.add("i");

    }



    /**
     *
     * @param redGo 是否红走棋
     * @param move 移动说明 如马二进三
     * @param board 二维棋盘 横坐标是a-i 总坐标是0-9
     * @return 开始坐标+结束坐标的拼接 如马二进三  初始坐标是h0  结束坐标是g2
     */
    public static final String convert(char[][] board,boolean redGo,String move){
        //定位初始坐标
        Integer[] from = buildFrom(board,redGo,move);
        //构建偏移量
        Integer[] offset = buildOffset(redGo,move,from);

        //构建返回结果
        StringBuilder result = new StringBuilder();
        result.append(INDEX_LIST.get(from[0])).append(9-from[1])
                .append(INDEX_LIST.get(from[0]+offset[0])).append(9-(from[1]+offset[1]));
        board[from[1]+offset[1]][from[0]+offset[0]] = board[from[1]][from[0]];
        board[from[1]][from[0]] = ' ';
        return result.toString();
    }

    private static Integer[] buildOffset(boolean redGo, String move,Integer[] from) {
        String first = String.valueOf(move.charAt(0));
        String second = String.valueOf(move.charAt(1));
        String third = String.valueOf(move.charAt(2));
        String forth = String.valueOf(move.charAt(3));
        Character piece = null;

        if(map.containsKey(first)){
            piece = redGo?Character.toUpperCase(map.get(first)):map.get(first);
        }else{
            piece = redGo?Character.toUpperCase(map.get(second)):map.get(second);
        }

        Integer[] result = new Integer[2];

        if('车'==piece || '炮'==piece || '兵'==piece||'卒'==piece || '帅'==piece||'将'==piece){
            if(third.equals("平")){
                int toJ = redGo? 9-NUMBER_MAP.get(forth) : Integer.parseInt(forth)-1;
                result[0] = toJ - from[0];
                return result;
            }
            if((third.equals("进") && redGo) || (third.equals("退") && !redGo)){
                result[1] = -Integer.parseInt(forth);
                return result;
            }else{
                result[1] = Integer.parseInt(forth);
                return result;
            }
        }
        if('相'==piece || '象'==piece){
            if((third.equals("进") && redGo) || (third.equals("退") && !redGo)){
                result[1] = -2 ;
                int toJ = redGo? 9-NUMBER_MAP.get(forth) : Integer.parseInt(forth)-1;
                result[0] =  toJ -from[0];
                return result;
            }else{
                result[1] =  2 ;
                int toJ = redGo? 9-NUMBER_MAP.get(forth) : Integer.parseInt(forth)-1;
                result[0] =  toJ -from[0];
                return result;
            }
        }
        if( '仕'==piece ||'士'==piece){
            if((third.equals("进") && redGo) || (third.equals("退") && !redGo)){
                result[1] = -1 ;
                int toJ = redGo? 9-NUMBER_MAP.get(forth) : Integer.parseInt(forth)-1;
                result[0] =  toJ -from[0];
                return result;
            }else{
                result[1] = 1 ;
                int toJ = redGo? 9-NUMBER_MAP.get(forth) : Integer.parseInt(forth)-1;
                result[0] =  toJ -from[0];
                return result;
            }
        }
        if('马' == piece){
            int toJ = redGo? 9-NUMBER_MAP.get(forth) : Integer.parseInt(forth)-1;
            if((third.equals("进") && redGo) || (third.equals("退") && !redGo)){
                result[0] =  toJ -from[0];
                if(Math.abs(result[0]) == 2){
                    result[1] = -1;
                }
                if(Math.abs(result[0]) == 1){
                    result[1] = -2;
                }
                return result;
            }else{
                result[0] =  toJ -from[0];
                if(Math.abs(result[0]) == 2){
                    result[1] = 1;
                }
                if(Math.abs(result[0]) == 1){
                    result[1] = 2;
                }
                return result;
            }
        }
        return null;
    }

    private static Integer[] buildFrom(char[][] board, boolean redGo, String move) {
        String first = String.valueOf(move.charAt(0));
        String second = String.valueOf(move.charAt(1));
        String third = String.valueOf(move.charAt(2));
        String forth = String.valueOf(move.charAt(3));
        Character piece = null;

        if(map.containsKey(first)){
            piece = redGo?Character.toUpperCase(map.get(first)):map.get(first);
        }else{
            piece = redGo?Character.toUpperCase(map.get(second)):map.get(second);
        }

        Integer[] result = new Integer[2];
        if(map.containsKey(first)){
            //说明是无前后棋子情况 定位初始坐标
            result[0] = redGo? 9-NUMBER_MAP.get(second) : Integer.parseInt(second)-1;

            if('车'==piece || '炮'==piece || '兵'==piece||'卒'==piece || '帅'==piece||'将'==piece){
                for(int i = 0 ;i<10;i++){
                    if(board[i][result[0]] == piece){
                        result[1] = i;
                        break;
                    }
                }
                return result;
            }
            if('相'==piece || '仕'==piece || '象'==piece||'士'==piece){
                if((third.equals("进") && redGo)|| (third.equals("退")&&!redGo)){
                    for(int i = 9 ;i>=0;i--){
                        if(board[i][result[0]] == piece){
                            result[1] = i;
                            break;
                        }
                    }
                    return result;
                }
                for(int i = 0 ;i<10;i++){
                    if(board[i][result[0]] == piece){
                        result[1] = i;
                        break;
                    }
                }
                return result;
            }
        }

        //有前后棋子情况 遍历找出>=2个棋子的列
        for(int j = 0 ;j<9;j++){
            int count = 0;
            for(int i = 0;i<10;i++){
                if(piece == board[i][j]){
                    count++;
                }
            }
            if(count >=2){
                result[0] = j;
                break;
            }
        }
        if((first.equals("前") && redGo)||(first.equals("后")&&!redGo)){
            for(int i = 0 ;i<10;i++){
                if(board[i][result[0]] == piece){
                    result[1] = i;
                    break;
                }
            }
            return result;
        }
        if(first.equals("中")){
            int count =0;
            for(int i = 0 ;i<10;i++){
                if(board[i][result[0]] == piece){
                    count ++;
                    if(count == 2){
                        result[1] = i;
                    }
                }

            }
            return result;
        }
        if((first.equals("后") && redGo)||(first.equals("前")&&!redGo)){
            for(int i = 9 ;i>=0;i--){
                if(board[i][result[0]] == piece){
                    result[1] = i;
                    break;
                }
            }
            return result;
        }
        return result;
    }


    public static void main(String[] args) {


    }

    /**
     * 获取走棋的棋子
     * 
     * @return
     */
    private static char getPiece(boolean redGo,String move){


    }

}
