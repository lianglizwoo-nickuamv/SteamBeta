package com.example.steambeta.backend.library;

import com.example.steambeta.backend.context.ActorTransactions;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LibraryService {
    private final ActorTransactions transactions;
    private final LibraryRepository repository;
    public LibraryService(ActorTransactions transactions,LibraryRepository repository) {
        this.transactions=transactions; this.repository=repository;
    }
    public LibraryDtos.Identity me() {
        return transactions.execute(()->repository.identity()
            .map(r->new LibraryDtos.Identity(r.getStudentId(),r.getAlias(),r.getUniversityName()))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
    }
    private LibraryDtos.SubjectSummary dto(LibraryRepository.SummaryRow r) {
        return new LibraryDtos.SubjectSummary(r.getOfferingId(),r.getCode(),r.getName(),r.getTermCode(),r.getSectionCode(),
                r.getRequiredCount(),r.getCompletedCount(),r.getProgressPercent());
    }
    public List<LibraryDtos.SubjectSummary> library() {
        try {
            return transactions.execute(()->repository.library().stream().map(this::dto).toList());
        } catch (Exception ex) {
            // Usuario independiente de SteamBeta Campus sin matrícula institucional vinculada
            return Collections.emptyList();
        }
    }
    public LibraryDtos.Detail detail(UUID offeringId) {
        try {
            return transactions.execute(()->{
                var summary=repository.summary(offeringId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
                var activities=repository.activities(offeringId).stream().map(a->new LibraryDtos.Activity(
                        a.getActivityId(),a.getCode(),a.getName(),a.getRequired(),a.getCompleted())).toList();
                return new LibraryDtos.Detail(dto(summary),activities);
            });
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "offering_not_found");
        }
    }
}
