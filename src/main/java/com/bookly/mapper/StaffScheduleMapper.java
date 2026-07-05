package com.bookly.mapper;

import com.bookly.dto.*;
import com.bookly.entity.StaffSchedule;
import com.bookly.entity.StaffScheduleBreak;
import com.bookly.entity.StaffScheduleOverride;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for staff scheduling entities ↔ DTOs.
 */
@Mapper(componentModel = "spring")
public interface StaffScheduleMapper {

    @Mapping(target = "dayOfWeek", expression = "java(entity.getDayOfWeek().getValue())")
    @Mapping(target = "breaks", source = "breaks")
    @Mapping(target = "isWorkingDay", source = "workingDay")
    ScheduleDayResponse toResponse(StaffSchedule entity);

    @Mapping(target = "breakStart", source = "breakStart")
    @Mapping(target = "breakEnd", source = "breakEnd")
    BreakWindowResponse toBreakResponse(StaffScheduleBreak entity);

    @Mapping(target = "staffId", source = "staff.id")
    @Mapping(target = "isDayOff", source = "dayOff")
    ScheduleOverrideResponse toOverrideResponse(StaffScheduleOverride entity);
}
