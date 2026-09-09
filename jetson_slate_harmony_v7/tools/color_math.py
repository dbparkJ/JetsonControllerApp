"""sRGB relative luminance and Oklab/OKLCH. D65, no external packages.
Reference: W3C CSS Color 4 sample conversion code. Values are not comfort scores.
"""
import math

def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def linear_to_srgb(c):
    return 12.92*c if c <= 0.0031308 else 1.055*(c ** (1/2.4)) - 0.055

def rgb(h):
    return [int(h[i:i+2],16)/255 for i in (1,3,5)]

def luminance(h):
    r,g,b=map(srgb_to_linear,rgb(h)); return .2126*r+.7152*g+.0722*b

def contrast(a,b):
    hi,lo=sorted((luminance(a),luminance(b)),reverse=True)
    return (hi+.05)/(lo+.05)

def to_oklch(h):
    r,g,b=map(srgb_to_linear,rgb(h))
    l=(.4122214708*r+.5363325363*g+.0514459929*b)**(1/3)
    m=(.2119034982*r+.6806995451*g+.1073969566*b)**(1/3)
    s=(.0883024619*r+.2817188376*g+.6299787005*b)**(1/3)
    L=.2104542553*l+.7936177850*m-.0040720468*s
    a=1.9779984951*l-2.4285922050*m+.4505937099*s
    bb=.0259040371*l+.7827717662*m-.8086757660*s
    return L,math.hypot(a,bb),math.degrees(math.atan2(bb,a))%360

def from_oklch(L,C,h):
    a=C*math.cos(math.radians(h));b=C*math.sin(math.radians(h))
    l=(L+.3963377774*a+.2158037573*b)**3
    m=(L-.1055613458*a-.0638541728*b)**3
    s=(L-.0894841775*a-1.2914855480*b)**3
    cs=[4.0767416621*l-3.3077115913*m+.2309699292*s,
        -1.2684380046*l+2.6097574011*m-.3413193965*s,
        -.0041960863*l-.7034186147*m+1.7076147010*s]
    assert all(-.000001<=x<=1.000001 for x in cs),(L,C,h,cs)
    return '#'+''.join(f'{round(linear_to_srgb(max(0,min(1,x)))*255):02X}' for x in cs)
