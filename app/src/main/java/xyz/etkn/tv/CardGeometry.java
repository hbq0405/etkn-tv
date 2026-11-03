package xyz.etkn.tv;

/** Layout and request budgets share the same pixel geometry, including partially visible rows. */
final class CardGeometry {
    static int columns(int width,int mediumWidth,int gap,int imageSize){
        int medium=Math.max(1,(width+2*gap)/(mediumWidth+2*gap));
        return Math.max(1,medium+TvPreferences.IMAGE_MEDIUM-imageSize);
    }
    static int cardWidth(int width,int columns,int gap){return Math.max(1,width/columns-2*gap);}
    static int shelfCapacity(int width,int stride){return Math.max(1,(width+stride-1)/stride);}
    static int capacity(int width,int height,int columns,int gap,float aspect,int captionHeight,int rowGap){
        int stride=Math.max(1,Math.round(cardWidth(width,columns,gap)*aspect)+captionHeight+2*rowGap);
        return columns*Math.max(1,(Math.max(1,height)+stride-1)/stride);
    }
}
