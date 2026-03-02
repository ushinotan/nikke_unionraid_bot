from sqlalchemy import Column, BigInteger, Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from database import Base

class Guild(Base):
    __tablename__ = 'guilds'
    
    guild_id = Column(BigInteger, primary_key=True)
    created_at = Column(DateTime(timezone=True))  # DB default now()
    
    raids = relationship("UnionRaid", back_populates="guild", cascade="all, delete-orphan")

class UnionRaid(Base):
    __tablename__ = 'union_raids'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    guild_id = Column(BigInteger, ForeignKey('guilds.guild_id', ondelete='CASCADE'))
    raid_name = Column(String(255), nullable=False)
    start_time = Column(DateTime(timezone=True), nullable=False)
    end_time = Column(DateTime(timezone=True), nullable=False)
    notify_time = Column(DateTime(timezone=True), nullable=True)
    channel_id = Column(BigInteger, nullable=True)
    created_at = Column(DateTime(timezone=True))  # DB default now()
    
    guild = relationship("Guild", back_populates="raids")
    participants = relationship("RaidParticipant", back_populates="raid", cascade="all, delete-orphan")

class RaidParticipant(Base):
    __tablename__ = 'raid_participants'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    raid_id = Column(Integer, ForeignKey('union_raids.id', ondelete='CASCADE'))
    user_id = Column(BigInteger, nullable=False)
    username = Column(String(255), nullable=False)
    score = Column(Integer, default=0)
    joined_at = Column(DateTime(timezone=True))  # DB default now()
    
    raid = relationship("UnionRaid", back_populates="participants")

class RaidReport(Base):
    __tablename__ = 'raid_reports'

    id = Column(Integer, primary_key=True, autoincrement=True)
    raid_id = Column(Integer, ForeignKey('union_raids.id', ondelete='CASCADE'))
    user_id = Column(BigInteger, nullable=False)
    username = Column(String(255), nullable=False)
    difficulty = Column(String(32), nullable=False)  # 'normal' or 'hard'
    is_3t = Column(Integer, default=0)  # 1 = true, 0 = false
    reported_at = Column(DateTime(timezone=True))  # DB default now()

    raid = relationship("UnionRaid")