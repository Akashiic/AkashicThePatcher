package forestry.api.genetics;

public interface ISpeciesRoot {
    IAllele[] getDefaultTemplate();
    IChromosomeType[] getKaryotype();
    IChromosomeType getKaryotypeKey();
    IAllele[] getTemplate(String uid);
}
