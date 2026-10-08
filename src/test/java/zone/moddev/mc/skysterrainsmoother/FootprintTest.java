package zone.moddev.mc.skysterrainsmoother;

import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysterrainsmoother.internal.Footprint;
import static org.junit.jupiter.api.Assertions.*;

class FootprintTest {
    @Test void everyFootprintLevelAndRotation() {
        for(int level=1;level<=3;level++)for(int cardinal=0;cardinal<16;cardinal++)for(int diagonal=0;diagonal<16;diagonal++) {
            int expected=0;
            for(int d=0;d<4;d++)if((cardinal&(1<<d))!=0)expected|=Footprint.EDGES[d];
            if(level==3)expected|=diagonal;
            int mask=Footprint.mask(cardinal,diagonal,level);assertEquals(expected,mask);
            int shape=Footprint.shape(mask,level);
            if(mask==0)assertEquals(0,shape);
            else if(level==3&&Integer.bitCount(mask)==1)assertEquals(3,shape);
            else if(level>=2&&(mask==3||mask==6||mask==12||mask==9))assertEquals(2,shape);
            else assertEquals(1,shape);
            int rotation=((mask<<1)&15)|(mask>>3);
            assertEquals(shape,Footprint.shape(rotation,level));
            if(shape>1)assertEquals((Footprint.direction(mask,shape)+1)&3,Footprint.direction(rotation,shape));
        }
    }
    @Test void disconnectedAndThreeQuadrantFootprintsAreSlabs() {
        for(int mask:new int[]{5,10,7,11,13,14})assertEquals(1,Footprint.shape(mask,3));
    }
    @Test void borderPolicyNeverReadsTheUnownedNorthOrWestBorder() {
        for(int x=-2;x<=18;x++)for(int z=-2;z<=18;z++)
            assertEquals(x>=0&&x<=16&&z>=0&&z<=16,Footprint.allowedNeighbour(x,z));
        assertThrows(IllegalArgumentException.class,()->Footprint.mask(0,0,0));
        assertThrows(IllegalArgumentException.class,()->Footprint.shape(16,3));
        assertThrows(IllegalArgumentException.class,()->Footprint.shape(0,4));
    }
}
