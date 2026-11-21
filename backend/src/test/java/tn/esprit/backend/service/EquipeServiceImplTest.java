package tn.esprit.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.backend.entity.Entreprise;
import tn.esprit.backend.entity.Equipe;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.exception.ResourceNotFoundException;
import tn.esprit.backend.repository.EntrepriseRepository;
import tn.esprit.backend.repository.EquipeRepository;
import tn.esprit.backend.repository.ProjetRepository;
import tn.esprit.backend.service.impl.EquipeServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipeServiceImplTest {

    @Mock
    private EquipeRepository equipeRepository;
    @Mock
    private EntrepriseRepository entrepriseRepository;
    @Mock
    private ProjetRepository projetRepository;

    @InjectMocks
    private EquipeServiceImpl equipeService;

    private Equipe equipe;

    @BeforeEach
    void setUp() {
        equipe = Equipe.builder().id(1L).nom("Alpha").specialite("Backend").build();
    }

    @Test
    void addEquipeShouldSave() {
        when(equipeRepository.save(equipe)).thenReturn(equipe);
        assertSame(equipe, equipeService.addEquipe(equipe));
        verify(equipeRepository).save(equipe);
    }

    @Test
    void updateEquipeShouldSave() {
        when(equipeRepository.save(equipe)).thenReturn(equipe);
        assertEquals("Alpha", equipeService.updateEquipe(equipe).getNom());
    }

    @Test
    void deleteEquipeShouldDelegate() {
        equipeService.deleteEquipe(1L);
        verify(equipeRepository).deleteById(1L);
    }

    @Test
    void getEquipeByIdShouldReturnEquipeOrNull() {
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(equipeRepository.findById(2L)).thenReturn(Optional.empty());
        assertSame(equipe, equipeService.getEquipeById(1L));
        assertNull(equipeService.getEquipeById(2L));
    }

    @Test
    void getAllAndByEntrepriseShouldReturnLists() {
        when(equipeRepository.findAll()).thenReturn(List.of(equipe));
        when(equipeRepository.findByEntrepriseId(5L)).thenReturn(List.of(equipe));
        assertEquals(1, equipeService.getAllEquipes().size());
        assertEquals(1, equipeService.getEquipesByEntreprise(5L).size());
    }

    @Test
    void assignEquipeToEntrepriseShouldLinkThem() {
        Entreprise entreprise = Entreprise.builder().id(5L).nom("Esprit").build();
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(entreprise));
        when(equipeRepository.save(any(Equipe.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipe result = equipeService.assignEquipeToEntreprise(1L, 5L);

        assertSame(entreprise, result.getEntreprise());
    }

    @Test
    void assignEquipeToEntrepriseShouldFailWhenMissing() {
        when(equipeRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> equipeService.assignEquipeToEntreprise(1L, 5L));

        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> equipeService.assignEquipeToEntreprise(1L, 5L));
        verify(equipeRepository, never()).save(any());
    }

    @Test
    void assignEquipeToProjetShouldAddProjet() {
        Projet projet = Projet.builder().id(3L).sujet("CI/CD").build();
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(projetRepository.findById(3L)).thenReturn(Optional.of(projet));
        when(equipeRepository.save(any(Equipe.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipe result = equipeService.assignEquipeToProjet(1L, 3L);

        assertTrue(result.getProjets().contains(projet));
    }

    @Test
    void assignEquipeToProjetShouldFailWhenMissing() {
        when(equipeRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> equipeService.assignEquipeToProjet(1L, 3L));

        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(projetRepository.findById(3L)).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> equipeService.assignEquipeToProjet(1L, 3L));
        assertEquals("Projet not found with id 3", ex.getMessage());
    }
}
