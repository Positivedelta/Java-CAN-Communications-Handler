package bitparallel.communication;

//
// (c) Bit Parallel Ltd, November 2021
//

public class CanFilter
{
    private final int mask;
    private final int filter;

    public CanFilter(final int mask, final int filter)
    {
        this.mask = mask;
        this.filter = filter;
    }

    public int getMask()
    {
        return mask;
    }

    public int getFilter()
    {
        return filter;
    }
}
