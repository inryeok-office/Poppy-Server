package team.inreok.poppyserver.domain.session.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface BlockRevisionJpaRepository : JpaRepository<BlockRevisionEntity, BlockRevisionEntityId>
