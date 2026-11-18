package com.thecreativecafe.autoprint;

import android.bluetooth.*;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.util.*;
import java.util.UUID;

public class ReceiptPrinter {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static void print(Context context, String address, String orderId, Map<String,Object> order) throws Exception {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(address);
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP);
        socket.connect();
        try (OutputStream out = socket.getOutputStream()) {
            out.write(new byte[]{0x1B,0x40});
            Bitmap bitmap = makeReceipt(orderId, order);
            out.write(toRaster(bitmap));
            out.write(new byte[]{0x0A,0x0A,0x0A,0x0A});
            out.flush();
        } finally { try { socket.close(); } catch (Exception ignored) {} }
    }

    private static Bitmap makeReceipt(String id, Map<String,Object> order) {
        int width = 384;
        Bitmap temp = Bitmap.createBitmap(width, 1800, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(temp);
        c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        int y = 30;
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(25);
        y = center(c,p,"THE CREATIVE CAFE",y,width);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(17);
        y = center(c,p,"સુકૂન का दूसरा नाम — कॉफी।",y+4,width);
        y += 15;
        y = line(c,p,y,width);
        p.setTextSize(19);
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        y = left(c,p,"TOKEN: "+id.substring(0, Math.min(8,id.length())).toUpperCase(),y+24);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        y = left(c,p,"Name: "+safe(order.get("customerName")),y+28);
        y = left(c,p,"Mobile: "+safe(order.get("mobile")),y+27);
        y = line(c,p,y+8,width);
        p.setTextSize(17);
        Object raw = order.get("items");
        if (raw instanceof List) {
            for (Object obj : (List<?>)raw) {
                if (!(obj instanceof Map)) continue;
                Map<?,?> item = (Map<?,?>)obj;
                String name = safe(item.get("name")) + (item.get("size") == null ? "" : " ("+safe(item.get("size"))+")");
                y = left(c,p,name,y+24);
                y = left(c,p,"  "+safe(item.get("quantity"))+" x Rs "+safe(item.get("price"))+" = Rs "+safe(item.get("lineTotal")),y+22);
            }
        }
        y = line(c,p,y+12,width);
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        p.setTextSize(23);
        y = left(c,p,"TOTAL: Rs "+safe(order.get("total")),y+28);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(16);
        y = center(c,p,"Thank you! Visit Again.",y+40,width);
        Bitmap result = Bitmap.createBitmap(width, Math.max(1,y+24), Bitmap.Config.ARGB_8888);
        new Canvas(result).drawBitmap(temp,0,0,null);
        temp.recycle();
        return result;
    }

    private static int center(Canvas c, Paint p, String s, int y, int width) {
        c.drawText(s, (width-p.measureText(s))/2, y, p); return y+(int)(p.getTextSize()*1.5f);
    }
    private static int left(Canvas c, Paint p, String s, int y) {
        c.drawText(s, 8, y, p); return y+(int)(p.getTextSize()*1.45f);
    }
    private static int line(Canvas c, Paint p, int y, int width) {
        c.drawLine(8,y,width-8,y,p); return y+8;
    }
    private static String safe(Object o) { return o == null ? "" : String.valueOf(o); }

    private static byte[] toRaster(Bitmap b) throws IOException {
        int wBytes = (b.getWidth()+7)/8, h = b.getHeight();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x1D,0x76,0x30,0x00,(byte)(wBytes&255),(byte)((wBytes>>8)&255),(byte)(h&255),(byte)((h>>8)&255)});
        for (int y=0;y<h;y++) for (int xb=0;xb<wBytes;xb++) {
            int value=0;
            for (int bit=0;bit<8;bit++) {
                int x=xb*8+bit;
                if (x<b.getWidth()) {
                    int color=b.getPixel(x,y);
                    int gray=(Color.red(color)+Color.green(color)+Color.blue(color))/3;
                    if (gray<160) value |= (0x80>>bit);
                }
            }
            out.write(value);
        }
        b.recycle();
        return out.toByteArray();
    }
}
