Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Drawing.Drawing2D;
using System.IO;
public static class NimbusSpriteExport {
  static readonly Color[] Palette = {Color.FromArgb(248,252,255),Color.FromArgb(228,243,255),Color.FromArgb(198,229,250)};
  public static void Run(string source, string output) {
    using (var input = new Bitmap(source))
    using (var sheet = new Bitmap(128,128,PixelFormat.Format32bppArgb))
    using (var contact = new Bitmap(768,768,PixelFormat.Format32bppArgb)) {
      int left=input.Width, right=0, top=input.Height,bottom=0;
      for(int f=0;f<8;f++) {
        int y0=(int)Math.Round(f*input.Height/8.0),y1=(int)Math.Round((f+1)*input.Height/8.0);
        for(int y=y0;y<y1;y++) for(int x=0;x<input.Width;x++) {
          Color c=input.GetPixel(x,y);
          if(c.A>=235 && Math.Min(c.R,Math.Min(c.G,c.B))>140) {
            left=Math.Min(left,x);right=Math.Max(right,x);top=Math.Min(top,y-y0);bottom=Math.Max(bottom,y-y0);
          }
        }
      }
      double sx=(right-left+1)/120.0,sy=(bottom-top+1)/12.0;
      for(int f=0;f<8;f++) {
        int y0=(int)Math.Round(f*input.Height/8.0),y1=(int)Math.Round((f+1)*input.Height/8.0);
        using(var raw = new Bitmap(128,16,PixelFormat.Format32bppArgb))
        using(var frame = new Bitmap(128,16,PixelFormat.Format32bppArgb)) {
          for(int y=2;y<14;y++) for(int x=4;x<124;x++) {
            int n=0,r=0,g=0,b=0;
            for(int j=-1;j<=1;j++) for(int i=-1;i<=1;i++) {
              int px=(int)Math.Floor(left+(x-4+.5+i*.25)*sx);
              int py=(int)Math.Floor(y0+top+(y-2+.5+j*.25)*sy);
              if(px<0||px>=input.Width||py<y0||py>=y1)continue;
              Color c=input.GetPixel(px,py);
              if(c.A>=235 && Math.Min(c.R,Math.Min(c.G,c.B))>140){n++;r+=c.R;g+=c.G;b+=c.B;}
            }
            if(n<4)continue;
            r/=n;g/=n;b/=n;
            int best=0,distance=int.MaxValue;
            for(int k=0;k<Palette.Length;k++){
              Color c=Palette[k]; int d=(r-c.R)*(r-c.R)+(g-c.G)*(g-c.G)+(b-c.B)*(b-c.B);
              if(d<distance){distance=d;best=k;}
            }
            raw.SetPixel(x,y,Palette[best]);
          }
          int minY=16,maxY=-1;
          for(int y=0;y<16;y++)for(int x=4;x<28;x++)if(raw.GetPixel(x,y).A>0){minY=Math.Min(minY,y);maxY=Math.Max(maxY,y);}
          int shift=(int)Math.Round(8-(minY+maxY)/2.0,MidpointRounding.ToEven);
          for(int y=0;y<16;y++)for(int x=0;x<128;x++)if(y+shift>=0&&y+shift<16)frame.SetPixel(x,y+shift,raw.GetPixel(x,y));
          frame.Save(Path.Combine(output,"frames",String.Format("nimbus_wind_trail_{0:00}.png",f)),ImageFormat.Png);
          using(var sg=Graphics.FromImage(sheet)){sg.CompositingMode=CompositingMode.SourceCopy;sg.DrawImageUnscaled(frame,0,f*16);}
          using(var preview = new Bitmap(768,96,PixelFormat.Format32bppArgb))
          using(var pg = Graphics.FromImage(preview)) {
            for(int y=0;y<96;y+=12)for(int x=0;x<768;x+=12){using(var brush=new SolidBrush((x/12+y/12)%2==0?Color.FromArgb(30,39,50):Color.FromArgb(38,48,60)))pg.FillRectangle(brush,x,y,12,12);}
            // Integer nearest-neighbor export preserves the exact source texels.
            for(int y=0;y<16;y++)for(int x=0;x<128;x++){
              Color c=frame.GetPixel(x,y);if(c.A>0)using(var brush=new SolidBrush(c))pg.FillRectangle(brush,x*6,y*6,6,6);
            }
            preview.Save(Path.Combine(output,"preview-frames",String.Format("preview_{0:00}.png",f)),ImageFormat.Png);
            using(var cg=Graphics.FromImage(contact))cg.DrawImageUnscaled(preview,0,f*96);
          }
        }
      }
      sheet.Save(Path.Combine(output,"nimbus_wind_trail.png"),ImageFormat.Png);
      contact.Save(Path.Combine(output,"nimbus_wind_trail_contact_sheet.png"),ImageFormat.Png);
      Console.WriteLine("Exported 8 registered 128x16 RGBA frames; sheet 128x128.");
    }
  }
}
"@
[NimbusSpriteExport]::Run('C:/Users/LENOVO/.codex/generated_images/01a1c483-2e64-7832-9273-bf923a8149f1/exec-dcd23a3e-6938-44e9-8163-4ce3ecea3380.png', (Join-Path $PSScriptRoot '.'))

